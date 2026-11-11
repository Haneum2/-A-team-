package org.mnu.portfolio;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 토스증권 Open API 로 종목의 현재가를 조회한다.
 *
 * - 인증: OAuth 2.0 Client Credentials (POST /oauth2/token). 토큰은 메모리에 캐시하고 만료 직전에 다시 받는다.
 * - 시세: GET /api/v1/prices?symbols=005930,AAPL  (Authorization: Bearer 토큰)
 * - 조회에 실패하면 마지막으로 받아 둔 시세를 stale 로 표시해서 돌려준다 (UC-004 시나리오 6a).
 *
 * client id / secret 은 api.properties 의 toss.client.id, toss.client.secret 에서 읽는다.
 * 비밀값(secret, 토큰)은 로그에 절대 남기지 않는다.
 */
@Service
public class TossPriceClient {

	private static final Logger logger = LoggerFactory.getLogger(TossPriceClient.class);

	private static final String BASE_URL = "https://openapi.tossinvest.com";
	private static final int HTTP_TIMEOUT = 10_000;
	/** 같은 종목을 이 시간 안에 다시 조회하면 API 를 부르지 않고 캐시를 쓴다 (호출 한도 보호). */
	private static final long QUOTE_TTL_MS = 10_000L;
	/** 토큰 만료 이 시간 전부터는 새 토큰을 받는다. */
	private static final long TOKEN_MARGIN_MS = 60_000L;
	/** 한 번의 요청에 담는 종목 수. (문서상 상한을 확인하지 못해 보수적으로 잡았다) */
	private static final int CHUNK_SIZE = 20;

	@Value("${toss.client.id:}")
	private String clientId;

	@Value("${toss.client.secret:}")
	private String clientSecret;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String accessToken;
	private long tokenExpiresAt;

	/** stockCode -> 마지막으로 받은 시세 */
	private final Map<String, Quote> lastQuotes = new ConcurrentHashMap<String, Quote>();

	public boolean isConfigured() {
		return clientId != null && !clientId.trim().isEmpty()
				&& clientSecret != null && !clientSecret.trim().isEmpty();
	}

	/**
	 * 종목코드들의 현재가를 조회한다.
	 * 일부 또는 전부 조회에 실패하면, 마지막으로 받은 시세가 있는 종목은 그 값을 stale=true 로 채워 준다.
	 * 시세를 구하지 못한 종목은 결과 맵에 들어 있지 않다.
	 */
	public PriceResult getQuotes(Collection<String> stockCodes) {
		Map<String, Quote> out = new HashMap<String, Quote>();
		if (stockCodes == null || stockCodes.isEmpty()) {
			return new PriceResult(out, false, null);
		}
		List<String> codes = new ArrayList<String>(new LinkedHashSet<String>(stockCodes));

		if (!isConfigured()) {
			logger.warn("토스증권 API 키가 설정되지 않았습니다. api.properties에 toss.client.id / toss.client.secret을 입력하세요.");
			return withFallback(out, codes, "시세 조회 설정이 되어 있지 않습니다. 관리자에게 문의해 주세요.");
		}

		List<String> need = new ArrayList<String>();
		for (String code : codes) {
			Quote q = lastQuotes.get(code);
			if (q != null && Duration.between(q.getFetchedAt(), LocalDateTime.now()).toMillis() < QUOTE_TTL_MS) {
				out.put(code, q);
			} else {
				need.add(code);
			}
		}

		String error = null;
		for (int from = 0; from < need.size(); from += CHUNK_SIZE) {
			List<String> chunk = need.subList(from, Math.min(from + CHUNK_SIZE, need.size()));
			try {
				Map<String, Quote> fetched = fetchChunk(chunk);
				lastQuotes.putAll(fetched);
				out.putAll(fetched);
			} catch (TossApiException e) {
				logger.warn("토스증권 시세 조회 오류: http={}, msg={}", e.getHttpCode(), e.getMessage());
				error = e.getMessage();
			} catch (Exception e) {
				logger.error("토스증권 시세 조회 실패: {}", e.toString());
				error = "시세를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
			}
		}
		return withFallback(out, codes, error);
	}

	/** 아직 시세가 없는 종목을 마지막 시세(stale)로 채워서 결과를 만든다. */
	private PriceResult withFallback(Map<String, Quote> out, List<String> codes, String error) {
		boolean stale = false;
		for (String code : codes) {
			if (!out.containsKey(code)) {
				Quote last = lastQuotes.get(code);
				if (last != null) {
					out.put(code, last);
					stale = true;
				}
			}
		}
		return new PriceResult(out, stale, error);
	}

	// ---- 토스 API 호출 ----

	private Map<String, Quote> fetchChunk(List<String> chunk) throws Exception {
		StringBuilder symbols = new StringBuilder();
		for (String code : chunk) {
			if (symbols.length() > 0) {
				symbols.append(',');
			}
			symbols.append(URLEncoder.encode(code, "UTF-8"));
		}
		String url = BASE_URL + "/api/v1/prices?symbols=" + symbols;

		HttpResult res = request("GET", url, null, getToken());
		if (res.code == 401) {
			// 토큰이 만료됐을 수 있으니 한 번만 새로 받아서 다시 시도한다.
			invalidateToken();
			res = request("GET", url, null, getToken());
		}
		if (res.code != 200) {
			throw new TossApiException(res.code, userMessage(res.code));
		}
		return parseQuotes(res.body);
	}

	/**
	 * 응답의 result 에서 시세를 꺼낸다. 문서/응답 형식이 배열이든 객체 안의 배열이든 단일 객체든 받아 주도록 방어적으로 읽는다.
	 * 각 항목: symbol, timestamp, lastPrice(문자열), currency
	 */
	private Map<String, Quote> parseQuotes(String body) throws Exception {
		JsonNode result = objectMapper.readTree(body).path("result");
		List<JsonNode> nodes = new ArrayList<JsonNode>();
		if (result.isArray()) {
			for (JsonNode n : result) {
				nodes.add(n);
			}
		} else if (result.has("symbol")) {
			nodes.add(result);
		} else {
			for (String name : new String[] { "items", "prices", "quotes" }) {
				if (result.path(name).isArray()) {
					for (JsonNode n : result.path(name)) {
						nodes.add(n);
					}
					break;
				}
			}
		}

		Map<String, Quote> quotes = new HashMap<String, Quote>();
		LocalDateTime now = LocalDateTime.now();
		for (JsonNode n : nodes) {
			String symbol = n.path("symbol").asText("");
			String price = n.path("lastPrice").asText("");
			if (symbol.isEmpty() || price.isEmpty()) {
				continue;
			}
			try {
				quotes.put(symbol, new Quote(new BigDecimal(price), n.path("currency").asText("KRW"),
						n.path("timestamp").asText(""), now));
			} catch (NumberFormatException e) {
				logger.warn("토스증권 시세 형식 오류: symbol={}", symbol);
			}
		}
		if (quotes.isEmpty()) {
			logger.warn("토스증권 시세 응답에서 읽을 수 있는 항목이 없습니다. 응답 형식을 확인하세요.");
		}
		return quotes;
	}

	private synchronized String getToken() throws Exception {
		long now = System.currentTimeMillis();
		if (accessToken != null && now < tokenExpiresAt - TOKEN_MARGIN_MS) {
			return accessToken;
		}
		String form = "grant_type=client_credentials"
				+ "&client_id=" + URLEncoder.encode(clientId.trim(), "UTF-8")
				+ "&client_secret=" + URLEncoder.encode(clientSecret.trim(), "UTF-8");
		HttpResult res = request("POST", BASE_URL + "/oauth2/token", form, null);
		if (res.code != 200) {
			throw new TossApiException(res.code, userMessage(res.code));
		}
		JsonNode json = objectMapper.readTree(res.body);
		String token = json.path("access_token").asText("");
		if (token.isEmpty()) {
			throw new TossApiException(res.code, "시세 조회 인증에 실패했습니다.");
		}
		accessToken = token;
		tokenExpiresAt = now + json.path("expires_in").asLong(3600L) * 1000L;
		return accessToken;
	}

	private synchronized void invalidateToken() {
		accessToken = null;
		tokenExpiresAt = 0L;
	}

	private String userMessage(int httpCode) {
		if (httpCode == 429) {
			return "시세 조회 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";
		}
		if (httpCode == 401 || httpCode == 403) {
			return "시세 조회 인증에 실패했습니다. API 키와 허용 IP 설정을 확인해 주세요.";
		}
		return "시세를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. (HTTP " + httpCode + ")";
	}

	@SuppressWarnings("deprecation")
	private HttpResult request(String method, String urlStr, String formBody, String bearerToken) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod(method);
		conn.setConnectTimeout(HTTP_TIMEOUT);
		conn.setReadTimeout(HTTP_TIMEOUT);
		conn.setRequestProperty("Accept", "application/json");
		if (bearerToken != null) {
			conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
		}
		if (formBody != null) {
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			OutputStream os = conn.getOutputStream();
			try {
				os.write(formBody.getBytes("UTF-8"));
			} finally {
				os.close();
			}
		}

		int code = conn.getResponseCode();
		InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
		String body = "";
		if (is != null) {
			BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			try {
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}
				body = sb.toString();
			} finally {
				br.close();
			}
		}
		return new HttpResult(code, body);
	}

	// ---- 내부/결과 클래스 ----

	private static class HttpResult {
		final int code;
		final String body;

		HttpResult(int code, String body) {
			this.code = code;
			this.body = body;
		}
	}

	private static class TossApiException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final int httpCode;

		TossApiException(int httpCode, String userMsg) {
			super(userMsg);
			this.httpCode = httpCode;
		}

		int getHttpCode() {
			return httpCode;
		}
	}

	/** 종목 하나의 현재가. */
	public static class Quote {
		private final BigDecimal price;
		private final String currency;
		private final String quoteTime;
		private final LocalDateTime fetchedAt;

		public Quote(BigDecimal price, String currency, String quoteTime, LocalDateTime fetchedAt) {
			this.price = price;
			this.currency = currency;
			this.quoteTime = quoteTime;
			this.fetchedAt = fetchedAt;
		}

		/** 현재가 */
		public BigDecimal getPrice() { return price; }
		/** 통화 (KRW, USD) */
		public String getCurrency() { return currency; }
		/** 토스가 알려준 시세 시각 (원문 문자열) */
		public String getQuoteTime() { return quoteTime; }
		/** 이 서버가 시세를 받아 온 시각. 화면의 "기준 시각"으로 쓴다. */
		public LocalDateTime getFetchedAt() { return fetchedAt; }
	}

	/** 시세 조회 결과. quotes 에 없는 종목은 시세를 구하지 못한 것이다. */
	public static class PriceResult {
		private final Map<String, Quote> quotes;
		private final boolean stale;
		private final String errorMsg;

		public PriceResult(Map<String, Quote> quotes, boolean stale, String errorMsg) {
			this.quotes = quotes != null ? quotes : Collections.<String, Quote>emptyMap();
			this.stale = stale;
			this.errorMsg = errorMsg;
		}

		public Map<String, Quote> getQuotes() { return quotes; }
		/** 일부 종목이 최신 시세가 아니라 마지막으로 받아 둔 시세이면 true */
		public boolean isStale() { return stale; }
		/** 사용자에게 보여 줄 오류 메시지 (없으면 null) */
		public String getErrorMsg() { return errorMsg; }
	}
}

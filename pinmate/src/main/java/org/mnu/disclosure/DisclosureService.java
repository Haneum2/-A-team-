package org.mnu.disclosure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DisclosureService implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(DisclosureService.class);

    private static final String CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=";
    private static final String LIST_URL      = "https://opendart.fss.or.kr/api/list.json";
    private static final long   CACHE_TTL_MIN = 10L;
    private static final int    HTTP_TIMEOUT  = 10_000;

    @Value("${dart.api.key:}")
    private String apiKey;

    // stock_code -> corp_code (상장사만)
    private volatile Map<String, String>   stockToCorpCode = new ConcurrentHashMap<>();
    // corp_code -> CorpInfo
    private volatile Map<String, CorpInfo> corpCodeToInfo  = new ConcurrentHashMap<>();

    // 공시 캐시: stockCode -> CachedResult
    private final Map<String, CachedResult> disclosureCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterPropertiesSet() {
        if (!isApiKeyConfigured()) {
            logger.warn("DART API 키가 설정되지 않았습니다. api.properties에 dart.api.key를 입력하세요.");
            return;
        }
        loadCorpCodes();
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
        sched.scheduleWithFixedDelay(new Runnable() {
            @Override public void run() { loadCorpCodes(); }
        }, 24L, 24L, TimeUnit.HOURS);
    }

    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    // ---- corpCode 관리 ----

    @SuppressWarnings("deprecation")
    private void loadCorpCodes() {
        logger.info("DART corpCode.xml 로딩 시작");
        try {
            URL url = new URL(CORP_CODE_URL + apiKey.trim());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HTTP_TIMEOUT);
            conn.setReadTimeout(30_000);
            conn.connect();

            int httpCode = conn.getResponseCode();
            if (httpCode != 200) {
                logger.error("DART corpCode.xml HTTP 오류: {}", httpCode);
                return;
            }

            Map<String, String>   newStockMap = new ConcurrentHashMap<>();
            Map<String, CorpInfo> newInfoMap  = new ConcurrentHashMap<>();

            try (ZipInputStream zis = new ZipInputStream(conn.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().toUpperCase().endsWith("CORPCODE.XML")) {
                        parseCorpCodeXml(zis, newStockMap, newInfoMap);
                        break;
                    }
                }
            }

            if (!newStockMap.isEmpty()) {
                stockToCorpCode = newStockMap;
                corpCodeToInfo  = newInfoMap;
                logger.info("DART corpCode 로딩 완료: 상장사 {}개", newStockMap.size());
            } else {
                logger.warn("DART corpCode.xml에서 상장사 데이터를 찾지 못했습니다 (ZIP 항목 이름 확인 필요).");
            }
        } catch (Exception e) {
            logger.error("DART corpCode.xml 로딩 실패 — 기존 Map 유지: {}", e.getMessage());
        }
    }

    private void parseCorpCodeXml(InputStream is,
                                   Map<String, String> stockMap,
                                   Map<String, CorpInfo> infoMap) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(is, new DefaultHandler() {
            private String corpCode, corpName, stockCode;
            private final StringBuilder buf = new StringBuilder();

            @Override public void startElement(String u, String l, String qName, Attributes a) {
                buf.setLength(0);
            }
            @Override public void characters(char[] ch, int s, int len) {
                buf.append(ch, s, len);
            }
            @Override public void endElement(String u, String l, String qName) {
                String val = buf.toString().trim();
                if ("corp_code".equals(qName))       { corpCode  = val; }
                else if ("corp_name".equals(qName))  { corpName  = val; }
                else if ("stock_code".equals(qName)) { stockCode = val; }
                else if ("list".equals(qName)) {
                    if (corpCode != null && corpName != null
                            && stockCode != null && !stockCode.isEmpty()) {
                        stockMap.put(stockCode, corpCode);
                        infoMap.put(corpCode, new CorpInfo(corpCode, corpName, stockCode));
                    }
                    corpCode = corpName = stockCode = null;
                }
            }
        });
    }

    // 종목명 부분일치 또는 종목코드 직접 검색 (최대 20개)
    public List<CorpInfo> searchByName(String keyword) {
        final String lower = keyword.toLowerCase();
        final String trimmed = keyword.trim();
        return corpCodeToInfo.values().stream()
                .filter(c -> c.getCorpName().toLowerCase().contains(lower)
                          || c.getStockCode().equals(trimmed))
                .limit(20)
                .collect(Collectors.toList());
    }

    // ---- 공시 조회 ----

    public DisclosureResult getDisclosures(String stockCode) {
        CachedResult cached = disclosureCache.get(stockCode);

        if (cached != null && !cached.isExpired() && !cached.fetchFailed) {
            return new DisclosureResult(cached.items, cached.fetchTime, false, null);
        }

        String corpCode = stockToCorpCode.get(stockCode);
        if (corpCode == null) {
            logger.warn("DART corpCode 조회 실패: stockCode={} (corpCode 맵에 없음)", stockCode);
            if (cached != null) {
                return new DisclosureResult(cached.items, cached.fetchTime, true, null);
            }
            return new DisclosureResult(
                    Collections.<DisclosureItem>emptyList(), null, false,
                    "해당 종목의 DART 등록 정보를 찾을 수 없습니다. 종목코드를 확인하거나 종목명으로 검색해 주세요.");
        }

        try {
            List<DisclosureItem> items = fetchFromDart(corpCode, stockCode);
            LocalDateTime now = LocalDateTime.now();
            disclosureCache.put(stockCode, new CachedResult(items, now, false));
            return new DisclosureResult(items, now, false, null);
        } catch (DartApiException e) {
            // DART API가 오류 코드를 반환한 경우: 캐시 없이 사용자 메시지 전달
            logger.warn("DART 공시 조회 오류: stockCode=" + stockCode + ", code=" + e.getCode() + ", msg=" + e.getMessage());
            return new DisclosureResult(Collections.<DisclosureItem>emptyList(), null, false, e.getMessage());
        } catch (Exception e) {
            // 네트워크 등 일시적 오류: 캐시 데이터가 있으면 stale 반환
            logger.error("DART 공시 조회 실패 (stockCode={}): {}", stockCode, e.getMessage());
            if (cached != null) {
                return new DisclosureResult(cached.items, cached.fetchTime, true, null);
            }
            return new DisclosureResult(Collections.<DisclosureItem>emptyList(), null, true, null);
        }
    }

    // 추후 포트폴리오/관심종목 연결용
    public List<DisclosureItem> getDisclosuresFor(List<String> stockCodes) {
        List<DisclosureItem> all = new ArrayList<>();
        for (String sc : stockCodes) {
            all.addAll(getDisclosures(sc).getItems());
        }
        Collections.sort(all, new Comparator<DisclosureItem>() {
            @Override public int compare(DisclosureItem a, DisclosureItem b) {
                return b.getReceiptDate().compareTo(a.getReceiptDate());
            }
        });
        return all;
    }

    // ---- 내부 헬퍼 ----

    @SuppressWarnings("deprecation")
    private List<DisclosureItem> fetchFromDart(String corpCode, String stockCode) throws Exception {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        String today          = LocalDate.now().format(fmt);
        String threeMonthsAgo = LocalDate.now().minusMonths(3).format(fmt);

        // URL에 apiKey가 포함되므로 로그에는 절대 urlStr을 찍지 않는다
        String urlStr = LIST_URL
                + "?crtfc_key=" + apiKey.trim()
                + "&corp_code=" + corpCode
                + "&bgn_de=" + threeMonthsAgo
                + "&end_de=" + today
                + "&page_count=100";

        String json = httpGet(urlStr);
        JsonNode root = objectMapper.readTree(json);

        String status  = root.path("status").asText();
        String message = root.path("message").asText();

        // 항상 status와 message를 로그로 남긴다 (key는 노출 안 함)
        logger.info("DART list.json: corp_code=" + corpCode
                + " bgn=" + threeMonthsAgo + " end=" + today
                + " status=" + status + " msg=" + message);

        if (!"000".equals(status)) {
            if ("013".equals(status)) {
                // 조회 결과 없음 — 에러 아님
                return Collections.emptyList();
            }
            throw new DartApiException(status, dartUserMessage(status, message));
        }

        List<DisclosureItem> result = new ArrayList<>();
        JsonNode list = root.path("list");
        if (list.isArray()) {
            for (JsonNode node : list) {
                DisclosureItem item = new DisclosureItem();
                item.setRcpNo(node.path("rcp_no").asText());
                item.setCorpName(node.path("corp_name").asText());
                item.setStockCode(stockCode);
                item.setTitle(node.path("report_nm").asText());
                item.setReceiptDate(node.path("rcept_dt").asText());
                item.setKey(isKeyDisclosure(item.getTitle()));
                result.add(item);
            }
        }
        logger.info("DART 공시 조회 완료: corp_code={}, 항목 {}건", corpCode, result.size());
        return result;
    }

    private String dartUserMessage(String status, String apiMessage) {
        if ("010".equals(status)) return "등록되지 않은 인증키입니다.";
        if ("011".equals(status)) return "사용할 수 없는 인증키입니다.";
        if ("020".equals(status)) return "일일 요청 한도를 초과했습니다. 내일 다시 시도해 주세요.";
        if ("100".equals(status)) return "요청 필드 오류입니다 (" + status + ").";
        if ("800".equals(status)) return "DART 시스템 점검 중입니다.";
        return "공시 정보를 불러오지 못했습니다 (사유: " + apiMessage + ").";
    }

    private boolean isKeyDisclosure(String title) {
        return title.contains("사업보고서") || title.contains("반기보고서") || title.contains("분기보고서")
                || title.contains("유상증자") || title.contains("무상증자")
                || title.contains("잠정실적") || title.contains("영업(잠정)");
    }

    @SuppressWarnings("deprecation")
    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(HTTP_TIMEOUT);
        conn.setReadTimeout(HTTP_TIMEOUT);
        conn.connect();

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("HTTP " + code);
        }

        BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

    // ---- 내부 클래스 ----

    private static class DartApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;
        DartApiException(String code, String userMsg) {
            super(userMsg);
            this.code = code;
        }
        String getCode() { return code; }
    }

    private static class CachedResult {
        final List<DisclosureItem> items;
        final LocalDateTime fetchTime;
        final boolean fetchFailed;

        CachedResult(List<DisclosureItem> items, LocalDateTime fetchTime, boolean fetchFailed) {
            this.items       = items;
            this.fetchTime   = fetchTime;
            this.fetchFailed = fetchFailed;
        }

        boolean isExpired() {
            return ChronoUnit.MINUTES.between(fetchTime, LocalDateTime.now()) >= CACHE_TTL_MIN;
        }
    }

    public static class DisclosureResult {
        private final List<DisclosureItem> items;
        private final LocalDateTime asOf;
        private final boolean stale;
        private final String errorMsg;

        public DisclosureResult(List<DisclosureItem> items, LocalDateTime asOf, boolean stale, String errorMsg) {
            this.items    = items != null ? items : Collections.<DisclosureItem>emptyList();
            this.asOf     = asOf;
            this.stale    = stale;
            this.errorMsg = errorMsg;
        }

        public List<DisclosureItem> getItems()    { return items;    }
        public LocalDateTime        getAsOf()     { return asOf;     }
        public boolean              isStale()     { return stale;    }
        public String               getErrorMsg() { return errorMsg; }
    }
}

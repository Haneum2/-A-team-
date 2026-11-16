package org.mnu.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Date;

/**
 * 알림 발송 조건과 중복 방지 키, 표시 문구 규칙. DB·외부 API 없이 단위 테스트할 수 있게 static 으로 모아 둔다.
 */
public final class NotificationRules {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private NotificationRules() {
	}

	/** (현재가 - 전일종가) / 전일종가 × 100, 소수 둘째 자리 반올림. 값이 없거나 전일종가가 0 이하면 null. */
	public static BigDecimal changeRate(BigDecimal price, BigDecimal prevClose) {
		if (price == null || prevClose == null || prevClose.signum() <= 0) {
			return null;
		}
		return price.subtract(prevClose).multiply(HUNDRED).divide(prevClose, 2, RoundingMode.HALF_UP);
	}

	/** 등락률의 절댓값이 기준(%) 이상이면 급등락이다. */
	public static boolean isSurge(BigDecimal rate, BigDecimal thresholdPercent) {
		return rate != null && thresholdPercent != null && thresholdPercent.signum() > 0
				&& rate.abs().compareTo(thresholdPercent) >= 0;
	}

	/** 기사가 기준 시각 이후에 올라왔는지. 발행 시각을 모르는 기사는 오래된 기사일 수 있어 보내지 않는다. */
	public static boolean isRecent(Date pubDate, Date cutoff) {
		return pubDate != null && !pubDate.before(cutoff);
	}

	/** 뉴스: 같은 기사(URL)는 한 회원에게 한 번만. URL 이 길어서 SHA-256 해시로 줄인다. */
	public static String newsKey(String link) {
		return "NEWS:" + sha256(link == null ? "" : link.trim());
	}

	/** 급등락: 같은 종목·같은 날·같은 방향(급등/급락)은 한 번만. */
	public static String priceKey(String stockCode, String yyyymmdd, BigDecimal rate) {
		return "PRICE:" + stockCode + ":" + yyyymmdd + ":" + (rate.signum() >= 0 ? "UP" : "DOWN");
	}

	/** 정기 리포트: kind(AM=모닝 브리핑, PM=마감 시황)별로 하루 한 번. */
	public static String reportKey(String kind, String yyyymmdd) {
		return "REPORT:" + kind + ":" + yyyymmdd;
	}

	/** DB 컬럼 길이를 넘지 않게 자른다. (VARCHAR2 는 바이트 기준이라 한글 3바이트를 고려해 글자 수를 넉넉히 줄여 둔다) */
	public static String truncate(String s, int maxChars) {
		if (s == null || s.length() <= maxChars) {
			return s;
		}
		return s.substring(0, Math.max(0, maxChars - 1)) + "…";
	}

	/** 72000 → "72,000", 182.5 → "182.5" */
	public static String formatPrice(BigDecimal price) {
		return price == null ? "-" : new DecimalFormat("#,##0.##").format(price);
	}

	/** 6.1 → "+6.10%", -5 → "-5.00%" */
	public static String formatRate(BigDecimal rate) {
		return rate == null ? "-" : new DecimalFormat("+0.00;-0.00").format(rate) + "%";
	}

	/** 통화 표시. KRW 는 "원", 그 밖에는 " USD" 처럼 붙인다. */
	public static String currencySuffix(String currency) {
		return currency == null || "KRW".equalsIgnoreCase(currency) ? "원" : " " + currency;
	}

	private static String sha256(String s) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(64);
			for (byte b : hash) {
				sb.append(String.format("%02x", b & 0xff));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // 모든 JVM 에 SHA-256 은 있다.
		}
	}
}

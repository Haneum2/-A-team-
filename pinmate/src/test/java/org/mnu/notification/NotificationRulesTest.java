package org.mnu.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.Test;

public class NotificationRulesTest {

	private static final BigDecimal FIVE = new BigDecimal("5");

	@Test
	public void 등락률은_전일종가_대비_소수둘째자리() {
		assertEquals(new BigDecimal("6.00"), NotificationRules.changeRate(new BigDecimal("53000"), new BigDecimal("50000")));
		assertEquals(new BigDecimal("-4.99"), NotificationRules.changeRate(new BigDecimal("95010"), new BigDecimal("100000")));
	}

	@Test
	public void 전일종가가_없거나_0이면_등락률_없음() {
		assertNull(NotificationRules.changeRate(new BigDecimal("100"), null));
		assertNull(NotificationRules.changeRate(null, new BigDecimal("100")));
		assertNull(NotificationRules.changeRate(new BigDecimal("100"), BigDecimal.ZERO));
	}

	@Test
	public void 기준_이상만_급등락() {
		assertTrue(NotificationRules.isSurge(new BigDecimal("5.00"), FIVE));   // 경계값 포함
		assertTrue(NotificationRules.isSurge(new BigDecimal("-7.31"), FIVE));  // 급락도 포함
		assertFalse(NotificationRules.isSurge(new BigDecimal("4.99"), FIVE));
		assertFalse(NotificationRules.isSurge(new BigDecimal("-4.99"), FIVE));
		assertFalse(NotificationRules.isSurge(null, FIVE));
	}

	@Test
	public void 최근_기사만_대상() {
		Date cutoff = new Date(1_000_000L);
		assertTrue(NotificationRules.isRecent(new Date(1_000_000L), cutoff));
		assertTrue(NotificationRules.isRecent(new Date(2_000_000L), cutoff));
		assertFalse(NotificationRules.isRecent(new Date(999_999L), cutoff));
		assertFalse(NotificationRules.isRecent(null, cutoff)); // 발행시각 모르는 기사는 보내지 않음
	}

	@Test
	public void 중복방지키() {
		String a = NotificationRules.newsKey("https://news.example.com/a");
		assertEquals(a, NotificationRules.newsKey(" https://news.example.com/a "));
		assertFalse(a.equals(NotificationRules.newsKey("https://news.example.com/b")));
		assertTrue(a.length() <= 100); // DEDUP_KEY VARCHAR2(100)

		assertEquals("PRICE:005930:20261116:UP", NotificationRules.priceKey("005930", "20261116", new BigDecimal("6.1")));
		assertEquals("PRICE:005930:20261116:DOWN", NotificationRules.priceKey("005930", "20261116", new BigDecimal("-6.1")));
		assertEquals("REPORT:AM:20261116", NotificationRules.reportKey("AM", "20261116"));
	}

	@Test
	public void 표시_형식() {
		assertEquals("72,000", NotificationRules.formatPrice(new BigDecimal("72000")));
		assertEquals("182.5", NotificationRules.formatPrice(new BigDecimal("182.50")));
		assertEquals("+6.10%", NotificationRules.formatRate(new BigDecimal("6.1")));
		assertEquals("-5.00%", NotificationRules.formatRate(new BigDecimal("-5")));
		assertEquals("원", NotificationRules.currencySuffix("KRW"));
		assertEquals(" USD", NotificationRules.currencySuffix("USD"));
	}

	@Test
	public void 긴_문자열은_자른다() {
		assertEquals("abc", NotificationRules.truncate("abc", 3));
		assertEquals("ab…", NotificationRules.truncate("abcd", 3));
		assertNull(NotificationRules.truncate(null, 3));
	}
}

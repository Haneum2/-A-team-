package org.mnu.notification;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.mnu.dao.NotificationDao;
import org.mnu.dao.PortfolioDao;
import org.mnu.news.NewsItem;
import org.mnu.news.NewsService;
import org.mnu.portfolio.Holding;
import org.mnu.portfolio.PortfolioService;
import org.mnu.portfolio.PortfolioService.Summary;
import org.mnu.portfolio.TossPriceClient;
import org.mnu.portfolio.TossPriceClient.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 스케줄러 (UC-011). root-context.xml 의 task:annotation-driven 으로 동작한다.
 *
 * - 뉴스      : 10분마다 보유 종목의 새 기사(최근 3시간)를 확인해서 즉시 발송
 * - 급등락    : 장중(평일 09:00~15:55) 5분마다 전일 종가 대비 ±기준% 이상이면 발송 (종목·날·방향별 1회)
 * - 모닝 브리핑: 평일 08:30 보유 종목 주요 뉴스
 * - 마감 시황 : 평일 15:40 보유 종목 종가·등락률·평가손익
 * - 재발송    : 5분마다 실패한 알림 재발송
 *
 * 공휴일(휴장일)은 따로 거르지 않는다. 휴장일 급등락은 시세가 그대로라 발송되지 않고, 리포트는 직전 거래일 기준으로 나간다.
 */
@Component
public class NotificationScheduler {

	private static final Logger logger = LoggerFactory.getLogger(NotificationScheduler.class);

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);

	/** 이보다 오래된 기사는 '새 기사'로 보지 않는다. */
	private static final long NEWS_WINDOW_MS = 3 * 3600_000L;
	/** 한 회원에게 한 번에 보내는 뉴스 알림 수 상한 (처음 켰을 때 몰아서 오는 것 방지) */
	private static final int NEWS_PER_RUN = 5;
	/** 모닝 브리핑: 전날 장 마감 이후 기사 */
	private static final long BRIEFING_WINDOW_MS = 18 * 3600_000L;
	private static final int BRIEFING_NEWS = 3;
	/** 시세가 이보다 오래됐으면(조회 실패로 예전 값이면) 급등락 판단에 쓰지 않는다. */
	private static final long QUOTE_FRESH_MS = 10 * 60_000L;

	@Value("${notify.enabled:true}")
	private boolean enabled;

	@Value("${notify.price.threshold:5}")
	private BigDecimal threshold;

	@Autowired
	private NotificationService notificationService;
	@Autowired
	private NotificationDao notificationDao;
	@Autowired
	private PortfolioDao portfolioDao;
	@Autowired
	private PortfolioService portfolioService;
	@Autowired
	private NewsService newsService;
	@Autowired
	private TossPriceClient priceClient;

	// ---- 뉴스 ----

	@Scheduled(fixedDelay = 10 * 60_000L, initialDelay = 60_000L)
	public void checkHoldingNews() {
		if (!enabled) {
			return;
		}
		Date cutoff = new Date(System.currentTimeMillis() - NEWS_WINDOW_MS);
		int total = 0;
		for (String userId : safeSubscribers(NotifyType.NEWS)) {
			try {
				total += sendNewsTo(userId, cutoff);
			} catch (Exception e) {
				logger.error("뉴스 알림 처리 실패: userId=" + userId, e);
			}
		}
		if (total > 0) {
			logger.info("뉴스 알림 {}건 발송", total);
		}
	}

	private int sendNewsTo(String userId, Date cutoff) {
		Map<String, String> codeByName = codeByName(portfolioDao.findByUser(userId));
		int count = 0;
		for (NewsItem item : newsService.getHoldingNews(new ArrayList<String>(codeByName.keySet())).getItems()) {
			if (count >= NEWS_PER_RUN) {
				break;
			}
			if (!NotificationRules.isRecent(item.getPubDate(), cutoff)) {
				continue;
			}
			String stockName = ownedStock(item, codeByName);
			String title = (stockName != null ? "[" + stockName + "] " : "") + item.getTitle();
			if (notificationService.publish(userId, NotifyType.NEWS, codeByName.get(stockName), title,
					item.getSource(), item.getLink(), NotificationRules.newsKey(item.getLink()))) {
				count++;
			}
		}
		return count;
	}

	// ---- 급등락 ----

	@Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Seoul")
	public void checkPriceSurge() {
		if (!enabled || !priceClient.isConfigured()) {
			return;
		}
		// 종목코드 -> 그 종목을 가진 (회원, 종목명) 목록. 시세는 종목별로 한 번만 조회한다.
		Map<String, List<String[]>> owners = new LinkedHashMap<String, List<String[]>>();
		for (String userId : safeSubscribers(NotifyType.PRICE)) {
			for (Holding h : portfolioDao.findByUser(userId)) {
				List<String[]> list = owners.get(h.getStockCode());
				if (list == null) {
					list = new ArrayList<String[]>();
					owners.put(h.getStockCode(), list);
				}
				list.add(new String[] { userId, h.getStockName() });
			}
		}
		if (owners.isEmpty()) {
			return;
		}

		Map<String, Quote> fresh = new HashMap<String, Quote>();
		for (Map.Entry<String, Quote> e : priceClient.getQuotes(owners.keySet()).getQuotes().entrySet()) {
			if (Duration.between(e.getValue().getFetchedAt(), LocalDateTime.now()).toMillis() < QUOTE_FRESH_MS) {
				fresh.put(e.getKey(), e.getValue());
			}
		}
		Map<String, BigDecimal> prevCloses = priceClient.getPreviousCloses(fresh);
		String day = LocalDate.now(KST).format(DAY_KEY);

		int total = 0;
		for (Map.Entry<String, Quote> e : fresh.entrySet()) {
			String code = e.getKey();
			Quote q = e.getValue();
			BigDecimal prev = prevCloses.get(code);
			BigDecimal rate = NotificationRules.changeRate(q.getPrice(), prev);
			if (!NotificationRules.isSurge(rate, threshold)) {
				continue;
			}
			String unit = NotificationRules.currencySuffix(q.getCurrency());
			String content = "현재가 " + NotificationRules.formatPrice(q.getPrice()) + unit
					+ " (전일 종가 " + NotificationRules.formatPrice(prev) + unit + " 대비 "
					+ NotificationRules.formatRate(rate) + ")";
			for (String[] owner : owners.get(code)) {
				String title = "[" + owner[1] + "] " + (rate.signum() >= 0 ? "급등 " : "급락 ")
						+ NotificationRules.formatRate(rate);
				try {
					if (notificationService.publish(owner[0], NotifyType.PRICE, code, title, content,
							"/stock/" + code, NotificationRules.priceKey(code, day, rate))) {
						total++;
					}
				} catch (Exception ex) {
					logger.error("급등락 알림 처리 실패: userId=" + owner[0] + ", code=" + code, ex);
				}
			}
		}
		if (total > 0) {
			logger.info("급등락 알림 {}건 발송", total);
		}
	}

	// ---- 정기 리포트 ----

	@Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Seoul")
	public void sendMorningBriefing() {
		if (!enabled) {
			return;
		}
		LocalDate today = LocalDate.now(KST);
		Date cutoff = new Date(System.currentTimeMillis() - BRIEFING_WINDOW_MS);
		for (String userId : safeSubscribers(NotifyType.REPORT)) {
			try {
				List<Holding> holdings = portfolioDao.findByUser(userId);
				Map<String, String> codeByName = codeByName(holdings);
				StringBuilder sb = new StringBuilder();
				sb.append("보유 종목 ").append(holdings.size()).append("개 · 장 시작 전 확인해 보세요.\n\n밤사이 주요 뉴스\n");
				int n = 0;
				for (NewsItem item : newsService.getHoldingNews(new ArrayList<String>(codeByName.keySet())).getItems()) {
					if (n >= BRIEFING_NEWS) {
						break;
					}
					if (!NotificationRules.isRecent(item.getPubDate(), cutoff)) {
						continue;
					}
					String stockName = ownedStock(item, codeByName);
					sb.append("· ").append(stockName != null ? "[" + stockName + "] " : "").append(item.getTitle());
					if (item.getSource() != null && !item.getSource().isEmpty()) {
						sb.append(" (").append(item.getSource()).append(')');
					}
					sb.append('\n');
					n++;
				}
				if (n == 0) {
					sb.append("· 새로 올라온 보유 종목 뉴스가 없습니다.\n");
				}
				notificationService.publish(userId, NotifyType.REPORT, null,
						"모닝 브리핑 · " + today.format(DAY_TITLE), sb.toString().trim(),
						"/news?tab=holding", NotificationRules.reportKey("AM", today.format(DAY_KEY)));
			} catch (Exception e) {
				logger.error("모닝 브리핑 처리 실패: userId=" + userId, e);
			}
		}
	}

	@Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
	public void sendClosingReport() {
		if (!enabled) {
			return;
		}
		if (!priceClient.isConfigured()) {
			logger.warn("토스증권 API 키가 없어 마감 시황 리포트를 보내지 않습니다.");
			return;
		}
		LocalDate today = LocalDate.now(KST);
		for (String userId : safeSubscribers(NotifyType.REPORT)) {
			try {
				Summary summary = portfolioService.getSummary(userId);
				if (summary.isNoHoldings()) {
					continue;
				}
				List<String> codes = new ArrayList<String>();
				for (PortfolioService.Item item : summary.getItems()) {
					codes.add(item.getStockCode());
				}
				Map<String, Quote> quotes = priceClient.getQuotes(codes).getQuotes();
				Map<String, BigDecimal> prevCloses = priceClient.getPreviousCloses(quotes);

				StringBuilder sb = new StringBuilder();
				if (summary.isHasPrice()) {
					sb.append("총 평가금액 ").append(NotificationRules.formatPrice(summary.getTotalEval())).append("원 · 평가손익 ")
							.append(summary.getTotalProfit().signum() >= 0 ? "+" : "")
							.append(NotificationRules.formatPrice(summary.getTotalProfit())).append("원 (")
							.append(NotificationRules.formatRate(summary.getTotalRate())).append(")\n\n");
				}
				sb.append("보유 종목 오늘 등락\n");
				for (PortfolioService.Item item : summary.getItems()) {
					Quote q = quotes.get(item.getStockCode());
					sb.append("· ").append(item.getStockName()).append(' ');
					if (q == null) {
						sb.append("시세 없음\n");
						continue;
					}
					sb.append(NotificationRules.formatPrice(q.getPrice())).append(NotificationRules.currencySuffix(q.getCurrency()))
							.append(" (").append(NotificationRules.formatRate(
									NotificationRules.changeRate(q.getPrice(), prevCloses.get(item.getStockCode()))))
							.append(")\n");
				}
				notificationService.publish(userId, NotifyType.REPORT, null,
						"마감 시황 · " + today.format(DAY_TITLE), sb.toString().trim(),
						"/portfolio", NotificationRules.reportKey("PM", today.format(DAY_KEY)));
			} catch (Exception e) {
				logger.error("마감 시황 처리 실패: userId=" + userId, e);
			}
		}
	}

	// ---- 재발송 ----

	@Scheduled(fixedDelay = 5 * 60_000L, initialDelay = 2 * 60_000L)
	public void retryFailed() {
		if (!enabled) {
			return;
		}
		try {
			int n = notificationService.retryFailed();
			if (n > 0) {
				logger.info("실패한 알림 {}건 재발송 시도", n);
			}
		} catch (Exception e) {
			logger.error("알림 재발송 처리 실패", e);
		}
	}

	// ---- 공통 ----

	/** DB 오류가 나도 스케줄러 스레드가 죽지 않게 빈 목록을 돌려준다. */
	private List<String> safeSubscribers(NotifyType type) {
		try {
			return notificationDao.findSubscribersWithHoldings(type);
		} catch (Exception e) {
			logger.error("알림 대상 회원 조회 실패: type=" + type, e);
			return new ArrayList<String>();
		}
	}

	/** 종목명 -> 종목코드 (뉴스는 종목명으로 검색하므로) */
	private static Map<String, String> codeByName(List<Holding> holdings) {
		Map<String, String> map = new LinkedHashMap<String, String>();
		for (Holding h : holdings) {
			map.put(h.getStockName(), h.getStockCode());
		}
		return map;
	}

	/**
	 * 기사에 붙은 관련 종목 중 이 회원이 보유한 첫 종목명.
	 * (NewsService 캐시가 회원 간에 공유돼 relatedStocks 에 다른 회원의 종목이 섞일 수 있어서 보유 종목으로 거른다)
	 */
	private static String ownedStock(NewsItem item, Map<String, String> codeByName) {
		for (String name : item.getRelatedStocks()) {
			if (codeByName.containsKey(name)) {
				return name;
			}
		}
		return null;
	}
}

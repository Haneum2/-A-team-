package org.mnu.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mnu.dao.PortfolioDao;
import org.mnu.dao.SectorDao;
import org.mnu.disclosure.CorpInfo;
import org.mnu.disclosure.DisclosureService;
import org.mnu.portfolio.TossPriceClient.PriceResult;
import org.mnu.portfolio.TossPriceClient.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 포트폴리오 관리(UC-004)와 테마/섹터 조회(UC-012)의 업무 로직.
 *
 * - 보유 종목 등록·수정·삭제 (입력 검증 포함)
 * - 현재가 기준 평가금액·손익(금액, 수익률)·전체 합계 계산
 * - 보유 종목의 테마/섹터와 섹터별 비중 계산
 *
 * 금액 계산은 모두 BigDecimal 로 한다. 국내 종목(원화)만 다루며, 원화가 아닌 시세는 계산에서 제외한다.
 */
@Service
public class PortfolioService {

	private static final Logger logger = LoggerFactory.getLogger(PortfolioService.class);

	static final String INVALID_NUMBER_MSG = "수량과 평단가는 0보다 큰 숫자로 입력해 주세요.";
	static final String UNCLASSIFIED = "미분류";

	private static final String KRW = "KRW";
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	/** 수량·평단가 컬럼이 NUMBER(18,4) 라서 정수부는 14자리까지 */
	private static final int MAX_INTEGER_DIGITS = 14;
	private static final int MAX_INPUT_LENGTH = 30;
	private static final DateTimeFormatter AS_OF_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private PortfolioDao portfolioDao;

	@Autowired
	private SectorDao sectorDao;

	@Autowired
	private TossPriceClient priceClient;

	@Autowired
	private DisclosureService disclosureService;

	// ---- 등록 · 수정 · 삭제 ----

	/**
	 * 보유 종목을 등록한다. 종목은 상장사 목록에 있어야 하고, 이름은 서버가 목록에서 찾아서 저장한다.
	 *
	 * @return 등록된 종목명
	 * @throws PortfolioException 입력 오류, 없는 종목, 이미 등록된 종목
	 */
	public String add(String userId, String stockCode, String quantityText, String avgPriceText) {
		BigDecimal quantity = parsePositive(quantityText);
		BigDecimal avgPrice = parsePositive(avgPriceText);

		CorpInfo corp = findListed(stockCode);
		if (corp == null) {
			throw new PortfolioException("종목을 찾을 수 없습니다. 검색 결과에서 종목을 선택해 주세요.");
		}
		try {
			portfolioDao.insert(userId, corp.getStockCode(), corp.getCorpName(), quantity, avgPrice);
		} catch (DuplicateKeyException e) {
			throw new PortfolioException("이미 등록된 종목입니다. 목록에서 수량과 평단가를 수정해 주세요.");
		}
		return corp.getCorpName();
	}

	/** 보유 수량과 평단가를 수정한다. */
	public void update(String userId, long holdingId, String quantityText, String avgPriceText) {
		BigDecimal quantity = parsePositive(quantityText);
		BigDecimal avgPrice = parsePositive(avgPriceText);
		if (!portfolioDao.update(userId, holdingId, quantity, avgPrice)) {
			throw new PortfolioException("수정할 종목을 찾을 수 없습니다.");
		}
	}

	/** 보유 종목을 삭제한다. */
	public void delete(String userId, long holdingId) {
		if (!portfolioDao.delete(userId, holdingId)) {
			throw new PortfolioException("삭제할 종목을 찾을 수 없습니다.");
		}
	}

	/**
	 * 입력 문자열을 0보다 큰 숫자(소수 4자리)로 바꾼다. 숫자가 아니거나 0 이하이거나 너무 크면 오류.
	 * 천 단위 쉼표는 허용한다.
	 */
	static BigDecimal parsePositive(String text) {
		if (text == null || text.trim().isEmpty() || text.length() > MAX_INPUT_LENGTH) {
			throw new PortfolioException(INVALID_NUMBER_MSG);
		}
		BigDecimal value;
		try {
			value = new BigDecimal(text.trim().replace(",", ""));
		} catch (NumberFormatException e) {
			throw new PortfolioException(INVALID_NUMBER_MSG);
		}
		// 1E+999999999 같은 값으로 setScale 을 하면 메모리를 많이 쓰므로 먼저 크기를 본다.
		if (value.signum() <= 0 || value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
			throw new PortfolioException(INVALID_NUMBER_MSG);
		}
		value = value.setScale(4, RoundingMode.HALF_UP);
		if (value.signum() <= 0) {
			throw new PortfolioException(INVALID_NUMBER_MSG);
		}
		return value;
	}

	private CorpInfo findListed(String stockCode) {
		String code = stockCode == null ? "" : stockCode.trim().toUpperCase();
		if (!code.matches("[0-9A-Z]{6}")) {
			return null;
		}
		for (CorpInfo c : disclosureService.searchByName(code)) {
			if (code.equals(c.getStockCode())) {
				return c;
			}
		}
		return null;
	}

	// ---- 종목 검색 ----

	/** 종목 검색(이름 또는 코드)을 쓸 수 있는지. DART 키가 없으면 종목 목록이 없다. */
	public boolean isSearchAvailable() {
		return disclosureService.isApiKeyConfigured();
	}

	/** 종목명 또는 종목코드로 검색하고, 각 종목의 테마/섹터를 함께 돌려준다. */
	public List<SearchResult> search(String keyword) {
		List<SearchResult> results = new ArrayList<SearchResult>();
		if (keyword == null || keyword.trim().isEmpty() || !disclosureService.isApiKeyConfigured()) {
			return results;
		}
		List<CorpInfo> found = disclosureService.searchByName(keyword.trim());
		List<String> codes = new ArrayList<String>();
		for (CorpInfo c : found) {
			codes.add(c.getStockCode());
		}
		Map<String, StockSector> sectors = loadSectors(codes);
		for (CorpInfo c : found) {
			StockSector s = sectors.get(c.getStockCode());
			results.add(new SearchResult(c.getStockCode(), c.getCorpName(),
					s != null ? s.getSector() : UNCLASSIFIED, s != null ? s.getTheme() : null));
		}
		return results;
	}

	/** 테마/섹터 조회가 실패해도(테이블이 아직 없는 경우 등) 화면의 나머지는 보여 주기 위해 빈 결과로 대신한다. */
	private Map<String, StockSector> loadSectors(List<String> codes) {
		try {
			return sectorDao.findByCodes(codes);
		} catch (DataAccessException e) {
			logger.warn("테마/섹터 조회 실패 — 미분류로 표시합니다: {}", e.getMessage());
			return new HashMap<String, StockSector>();
		}
	}

	// ---- 평가금액 · 손익 · 섹터 비중 계산 ----

	/**
	 * 회원의 포트폴리오를 현재가 기준으로 계산한다.
	 * 시세를 구하지 못한 종목은 목록에는 나오되 합계와 섹터 비중에서는 빠지고, unpricedCount 로 알린다.
	 */
	public Summary getSummary(String userId) {
		List<Holding> holdings = portfolioDao.findByUser(userId);
		Summary summary = new Summary();
		if (holdings.isEmpty()) {
			return summary;
		}

		List<String> codes = new ArrayList<String>();
		for (Holding h : holdings) {
			codes.add(h.getStockCode());
		}
		PriceResult prices = priceClient.getQuotes(codes);
		Map<String, StockSector> sectors = loadSectors(codes);

		LocalDateTime oldest = null;
		for (Holding h : holdings) {
			Item item = new Item(h);
			StockSector s = sectors.get(h.getStockCode());
			item.sector = s != null && s.getSector() != null ? s.getSector() : UNCLASSIFIED;
			item.theme = s != null ? s.getTheme() : null;
			item.costAmount = h.getQuantity().multiply(h.getAvgPrice());

			Quote q = prices.getQuotes().get(h.getStockCode());
			if (q != null && !KRW.equalsIgnoreCase(q.getCurrency())) {
				logger.warn("원화가 아닌 시세는 계산에서 제외합니다: {} ({})", h.getStockCode(), q.getCurrency());
				q = null;
			}
			if (q != null) {
				item.currentPrice = q.getPrice();
				item.evalAmount = h.getQuantity().multiply(q.getPrice());
				item.profit = item.evalAmount.subtract(item.costAmount);
				item.profitRate = rate(item.profit, item.costAmount);

				summary.totalCost = summary.totalCost.add(item.costAmount);
				summary.totalEval = summary.totalEval.add(item.evalAmount);
				if (oldest == null || q.getFetchedAt().isBefore(oldest)) {
					oldest = q.getFetchedAt();
				}
			} else {
				summary.unpricedCount++;
			}
			summary.items.add(item);
		}

		summary.totalProfit = summary.totalEval.subtract(summary.totalCost);
		summary.totalRate = rate(summary.totalProfit, summary.totalCost);
		summary.hasPrice = summary.unpricedCount < summary.items.size();
		summary.stale = prices.isStale();
		summary.errorMsg = prices.getErrorMsg();
		summary.asOfText = oldest != null ? oldest.format(AS_OF_FMT) : null;
		summary.sectorWeights = sectorWeights(summary);
		return summary;
	}

	/** 손익 ÷ 매입금액 × 100 (소수 둘째 자리 반올림). 매입금액이 0이면 0. */
	private static BigDecimal rate(BigDecimal profit, BigDecimal cost) {
		if (cost.signum() <= 0) {
			return BigDecimal.ZERO.setScale(2);
		}
		return profit.multiply(HUNDRED).divide(cost, 2, RoundingMode.HALF_UP);
	}

	/** 평가금액 기준 섹터별 비중. 큰 섹터부터 정렬하고, 비중은 소수 첫째 자리까지. */
	private static List<SectorWeight> sectorWeights(Summary summary) {
		List<SectorWeight> weights = new ArrayList<SectorWeight>();
		if (summary.totalEval.signum() <= 0) {
			return weights;
		}
		Map<String, BigDecimal> bySector = new LinkedHashMap<String, BigDecimal>();
		for (Item item : summary.items) {
			if (item.evalAmount == null) {
				continue;
			}
			BigDecimal sum = bySector.get(item.sector);
			bySector.put(item.sector, sum == null ? item.evalAmount : sum.add(item.evalAmount));
		}
		for (Map.Entry<String, BigDecimal> e : bySector.entrySet()) {
			BigDecimal percent = e.getValue().multiply(HUNDRED).divide(summary.totalEval, 1, RoundingMode.HALF_UP);
			weights.add(new SectorWeight(e.getKey(), e.getValue(), percent));
		}
		Collections.sort(weights, new Comparator<SectorWeight>() {
			@Override
			public int compare(SectorWeight a, SectorWeight b) {
				return b.getAmount().compareTo(a.getAmount());
			}
		});
		return weights;
	}

	// ---- 화면에 넘기는 결과 클래스 ----

	/** 보유 종목 한 줄. 시세를 구하지 못했으면 currentPrice, evalAmount, profit, profitRate 가 null 이다. */
	public static class Item {
		private final Holding holding;
		private String sector;
		private String theme;
		private BigDecimal costAmount;
		private BigDecimal currentPrice;
		private BigDecimal evalAmount;
		private BigDecimal profit;
		private BigDecimal profitRate;

		Item(Holding holding) {
			this.holding = holding;
		}

		public long getHoldingId() { return holding.getHoldingId(); }
		public String getStockCode() { return holding.getStockCode(); }
		public String getStockName() { return holding.getStockName(); }
		public BigDecimal getQuantity() { return holding.getQuantity(); }
		public BigDecimal getAvgPrice() { return holding.getAvgPrice(); }
		public String getSector() { return sector; }
		public String getTheme() { return theme; }
		/** 매입금액 = 수량 × 평단가 */
		public BigDecimal getCostAmount() { return costAmount; }
		public BigDecimal getCurrentPrice() { return currentPrice; }
		/** 평가금액 = 수량 × 현재가 */
		public BigDecimal getEvalAmount() { return evalAmount; }
		/** 손익 = 평가금액 − 매입금액 */
		public BigDecimal getProfit() { return profit; }
		/** 수익률(%) */
		public BigDecimal getProfitRate() { return profitRate; }
		public boolean isPriced() { return currentPrice != null; }

		/** 화면 색상용: 이익 up, 손실 down, 그 외 flat */
		public String getProfitClass() {
			if (profit == null || profit.signum() == 0) {
				return "flat";
			}
			return profit.signum() > 0 ? "up" : "down";
		}
	}

	/** 섹터 하나의 평가금액과 비중(%). */
	public static class SectorWeight {
		private final String sector;
		private final BigDecimal amount;
		private final BigDecimal percent;

		SectorWeight(String sector, BigDecimal amount, BigDecimal percent) {
			this.sector = sector;
			this.amount = amount;
			this.percent = percent;
		}

		public String getSector() { return sector; }
		public BigDecimal getAmount() { return amount; }
		public BigDecimal getPercent() { return percent; }
	}

	/** 포트폴리오 전체 결과. 합계는 시세가 있는 종목만 더한 값이다. */
	public static class Summary {
		private final List<Item> items = new ArrayList<Item>();
		private BigDecimal totalCost = BigDecimal.ZERO;
		private BigDecimal totalEval = BigDecimal.ZERO;
		private BigDecimal totalProfit = BigDecimal.ZERO;
		private BigDecimal totalRate = BigDecimal.ZERO;
		private int unpricedCount;
		private boolean hasPrice;
		private boolean stale;
		private String errorMsg;
		private String asOfText;
		private List<SectorWeight> sectorWeights = Collections.emptyList();

		public List<Item> getItems() { return items; }
		public boolean isNoHoldings() { return items.isEmpty(); }
		public BigDecimal getTotalCost() { return totalCost; }
		public BigDecimal getTotalEval() { return totalEval; }
		public BigDecimal getTotalProfit() { return totalProfit; }
		public BigDecimal getTotalRate() { return totalRate; }
		/** 시세를 구하지 못해 합계에서 빠진 종목 수 */
		public int getUnpricedCount() { return unpricedCount; }
		/** 시세가 있는 종목이 하나라도 있는지 */
		public boolean isHasPrice() { return hasPrice; }
		/** 일부 시세가 최신이 아니라 마지막으로 받아 둔 값이면 true */
		public boolean isStale() { return stale; }
		public String getErrorMsg() { return errorMsg; }
		/** 시세 기준 시각(가장 오래된 시세 기준) */
		public String getAsOfText() { return asOfText; }
		public List<SectorWeight> getSectorWeights() { return sectorWeights; }
		public String getTotalProfitClass() {
			return totalProfit.signum() == 0 ? "flat" : (totalProfit.signum() > 0 ? "up" : "down");
		}
	}

	/** 종목 검색 결과 한 줄. */
	public static class SearchResult {
		private final String stockCode;
		private final String stockName;
		private final String sector;
		private final String theme;

		SearchResult(String stockCode, String stockName, String sector, String theme) {
			this.stockCode = stockCode;
			this.stockName = stockName;
			this.sector = sector;
			this.theme = theme;
		}

		public String getStockCode() { return stockCode; }
		public String getStockName() { return stockName; }
		public String getSector() { return sector; }
		public String getTheme() { return theme; }
	}
}

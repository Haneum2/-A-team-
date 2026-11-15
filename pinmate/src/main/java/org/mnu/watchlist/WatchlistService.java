package org.mnu.watchlist;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mnu.dao.WatchlistDao;
import org.mnu.portfolio.TossPriceClient;
import org.mnu.portfolio.TossPriceClient.PriceResult;
import org.mnu.portfolio.TossPriceClient.Quote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 관심종목 등록·삭제·조회. 현재가와 전일 대비 등락은 토스증권 시세(TossPriceClient)로 채운다.
 */
@Service
public class WatchlistService {

	private static final DateTimeFormatter AS_OF_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private WatchlistDao watchlistDao;

	@Autowired
	private TossPriceClient priceClient;

	/** 이미 등록된 종목이면 DuplicateKeyException 이 그대로 올라간다. (컨트롤러에서 안내 메시지로 바꾼다) */
	public void add(String userId, String stockCode, String stockName) {
		watchlistDao.insert(userId, stockCode, stockName);
	}

	/** 삭제된 행이 없으면 false */
	public boolean delete(String userId, long watchId) {
		return watchlistDao.delete(userId, watchId);
	}

	public WatchlistView getView(String userId) {
		List<WatchItem> items = new ArrayList<WatchItem>(watchlistDao.findByUser(userId));
		if (items.isEmpty()) {
			return new WatchlistView(items, false, null, null);
		}

		List<String> codes = new ArrayList<String>();
		for (WatchItem it : items) {
			codes.add(it.getStockCode());
		}
		PriceResult prices = priceClient.getQuotes(codes);
		Map<String, Quote> quotes = prices.getQuotes();
		Map<String, BigDecimal> prevCloses = priceClient.getPreviousCloses(quotes);

		LocalDateTime asOf = null;
		for (WatchItem it : items) {
			Quote q = quotes.get(it.getStockCode());
			if (q == null) {
				continue;
			}
			it.setPrice(q.getPrice());
			if (asOf == null || q.getFetchedAt().isBefore(asOf)) {
				asOf = q.getFetchedAt(); // 가장 오래된 시세 기준으로 표시한다.
			}
			BigDecimal prev = prevCloses.get(it.getStockCode());
			if (prev != null && prev.signum() > 0) {
				BigDecimal diff = q.getPrice().subtract(prev);
				it.setChange(diff);
				it.setChangeRate(diff.multiply(BigDecimal.valueOf(100)).divide(prev, 2, RoundingMode.HALF_UP));
			}
		}
		return new WatchlistView(items, prices.isStale(), prices.getErrorMsg(),
				asOf != null ? asOf.format(AS_OF_FMT) : null);
	}
}

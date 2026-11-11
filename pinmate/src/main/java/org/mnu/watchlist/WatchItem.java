package org.mnu.watchlist;

import java.math.BigDecimal;

/** 관심종목 한 줄. 현재가/등락은 시세를 구했을 때만 채워진다. */
public class WatchItem {

	private long watchId;
	private String stockCode;
	private String stockName;
	/** 현재가. 시세를 못 구했으면 null */
	private BigDecimal price;
	/** 전일 대비 등락(원). 전일 종가를 못 구했으면 null */
	private BigDecimal change;
	/** 전일 대비 등락률(%). 전일 종가를 못 구했으면 null */
	private BigDecimal changeRate;

	public long getWatchId() { return watchId; }
	public void setWatchId(long watchId) { this.watchId = watchId; }

	public String getStockCode() { return stockCode; }
	public void setStockCode(String stockCode) { this.stockCode = stockCode; }

	public String getStockName() { return stockName; }
	public void setStockName(String stockName) { this.stockName = stockName; }

	public BigDecimal getPrice() { return price; }
	public void setPrice(BigDecimal price) { this.price = price; }

	public BigDecimal getChange() { return change; }
	public void setChange(BigDecimal change) { this.change = change; }

	public BigDecimal getChangeRate() { return changeRate; }
	public void setChangeRate(BigDecimal changeRate) { this.changeRate = changeRate; }

	public boolean isPriced() { return price != null; }

	public boolean isHasChange() { return change != null && changeRate != null; }

	/** 화면 색상 클래스. 국내 관례: 상승 빨강(up), 하락 파랑(down) */
	public String getChangeClass() {
		if (!isHasChange()) {
			return "muted";
		}
		int s = change.signum();
		return s > 0 ? "up" : (s < 0 ? "down" : "flat");
	}
}

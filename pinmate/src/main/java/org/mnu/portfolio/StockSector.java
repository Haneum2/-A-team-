package org.mnu.portfolio;

/**
 * STOCK_SECTOR 테이블의 한 행: 종목의 섹터와 테마.
 * 테마는 여러 개일 수 있고 쉼표로 이어진 한 문자열로 저장된다. (없으면 null)
 */
public class StockSector {

	private final String stockCode;
	private final String sector;
	private final String theme;

	public StockSector(String stockCode, String sector, String theme) {
		this.stockCode = stockCode;
		this.sector = sector;
		this.theme = theme;
	}

	public String getStockCode() { return stockCode; }
	public String getSector() { return sector; }
	public String getTheme() { return theme; }
}

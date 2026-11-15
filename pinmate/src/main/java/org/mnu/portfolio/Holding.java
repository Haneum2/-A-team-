package org.mnu.portfolio;

import java.math.BigDecimal;
import java.util.Date;

/**
 * PORTFOLIO 테이블의 한 행: 회원이 보유한 종목 하나.
 * 수량과 평단가는 소수점 오차를 피하려고 BigDecimal 로 다룬다.
 */
public class Holding {

	private long holdingId;
	private String stockCode;
	private String stockName;
	private BigDecimal quantity;
	private BigDecimal avgPrice;
	private Date regDate;
	private Date updDate;

	public long getHoldingId() { return holdingId; }
	public void setHoldingId(long holdingId) { this.holdingId = holdingId; }

	public String getStockCode() { return stockCode; }
	public void setStockCode(String stockCode) { this.stockCode = stockCode; }

	public String getStockName() { return stockName; }
	public void setStockName(String stockName) { this.stockName = stockName; }

	public BigDecimal getQuantity() { return quantity; }
	public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

	public BigDecimal getAvgPrice() { return avgPrice; }
	public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }

	public Date getRegDate() { return regDate; }
	public void setRegDate(Date regDate) { this.regDate = regDate; }

	public Date getUpdDate() { return updDate; }
	public void setUpdDate(Date updDate) { this.updDate = updDate; }
}

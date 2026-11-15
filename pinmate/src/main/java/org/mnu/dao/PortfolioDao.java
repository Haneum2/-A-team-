package org.mnu.dao;

import java.math.BigDecimal;
import java.util.List;

import org.mnu.portfolio.Holding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * PORTFOLIO 테이블 접근. 모든 조회·수정·삭제에 USER_ID 조건을 함께 걸어서
 * 다른 회원의 보유 종목은 건드릴 수 없게 한다.
 * 같은 종목을 두 번 등록하면 (USER_ID, STOCK_CODE) 유니크 제약 때문에 DuplicateKeyException 이 난다.
 */
@Repository
public class PortfolioDao {

	private static final String COLUMNS =
			"HOLDING_ID, STOCK_CODE, STOCK_NAME, QUANTITY, AVG_PRICE, REG_DATE, UPD_DATE";

	private static final RowMapper<Holding> MAPPER = (rs, i) -> {
		Holding h = new Holding();
		h.setHoldingId(rs.getLong("HOLDING_ID"));
		h.setStockCode(rs.getString("STOCK_CODE"));
		h.setStockName(rs.getString("STOCK_NAME"));
		h.setQuantity(rs.getBigDecimal("QUANTITY"));
		h.setAvgPrice(rs.getBigDecimal("AVG_PRICE"));
		h.setRegDate(rs.getTimestamp("REG_DATE"));
		h.setUpdDate(rs.getTimestamp("UPD_DATE"));
		return h;
	};

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** 보유 종목을 등록한다. */
	public void insert(String userId, String stockCode, String stockName, BigDecimal quantity, BigDecimal avgPrice) {
		jdbcTemplate.update(
				"INSERT INTO PORTFOLIO (HOLDING_ID, USER_ID, STOCK_CODE, STOCK_NAME, QUANTITY, AVG_PRICE) "
				+ "VALUES (PORTFOLIO_SEQ.NEXTVAL, ?, ?, ?, ?, ?)",
				userId, stockCode, stockName, quantity, avgPrice);
	}

	/** 보유 수량과 평단가를 수정한다. 수정된 행이 없으면(남의 종목이거나 없는 종목) false. */
	public boolean update(String userId, long holdingId, BigDecimal quantity, BigDecimal avgPrice) {
		return jdbcTemplate.update(
				"UPDATE PORTFOLIO SET QUANTITY = ?, AVG_PRICE = ?, UPD_DATE = SYSDATE "
				+ "WHERE HOLDING_ID = ? AND USER_ID = ?",
				quantity, avgPrice, holdingId, userId) > 0;
	}

	/** 보유 종목을 삭제한다. 삭제된 행이 없으면 false. */
	public boolean delete(String userId, long holdingId) {
		return jdbcTemplate.update(
				"DELETE FROM PORTFOLIO WHERE HOLDING_ID = ? AND USER_ID = ?",
				holdingId, userId) > 0;
	}

	/** 회원의 보유 종목 전체를 등록 순으로 반환한다. */
	public List<Holding> findByUser(String userId) {
		return jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM PORTFOLIO WHERE USER_ID = ? ORDER BY HOLDING_ID",
				MAPPER, userId);
	}

}

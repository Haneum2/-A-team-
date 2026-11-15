package org.mnu.dao;

import java.util.List;

import org.mnu.watchlist.WatchItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * WATCHLIST 테이블 접근. 모든 조회·삭제에 USER_ID 조건을 함께 걸어서 다른 회원의 관심종목은 건드릴 수 없게 한다.
 * 같은 종목을 두 번 등록하면 (USER_ID, STOCK_CODE) 유니크 제약 때문에 DuplicateKeyException 이 난다.
 */
@Repository
public class WatchlistDao {

	private static final RowMapper<WatchItem> MAPPER = (rs, i) -> {
		WatchItem w = new WatchItem();
		w.setWatchId(rs.getLong("WATCH_ID"));
		w.setStockCode(rs.getString("STOCK_CODE"));
		w.setStockName(rs.getString("STOCK_NAME"));
		return w;
	};

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public void insert(String userId, String stockCode, String stockName) {
		jdbcTemplate.update(
				"INSERT INTO WATCHLIST (WATCH_ID, USER_ID, STOCK_CODE, STOCK_NAME) VALUES (WATCHLIST_SEQ.NEXTVAL, ?, ?, ?)",
				userId, stockCode, stockName);
	}

	/** 삭제된 행이 없으면(남의 종목이거나 이미 지운 종목) false. */
	public boolean delete(String userId, long watchId) {
		return jdbcTemplate.update("DELETE FROM WATCHLIST WHERE WATCH_ID = ? AND USER_ID = ?", watchId, userId) > 0;
	}

	/** 회원의 관심종목 전체를 등록 순으로 반환한다. */
	public List<WatchItem> findByUser(String userId) {
		return jdbcTemplate.query(
				"SELECT WATCH_ID, STOCK_CODE, STOCK_NAME FROM WATCHLIST WHERE USER_ID = ? ORDER BY WATCH_ID",
				MAPPER, userId);
	}
}

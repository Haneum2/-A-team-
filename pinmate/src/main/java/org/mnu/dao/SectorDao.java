package org.mnu.dao;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mnu.portfolio.StockSector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * STOCK_SECTOR 테이블 접근. 종목코드로 테마/섹터를 조회한다.
 */
@Repository
public class SectorDao {

	private static final RowMapper<StockSector> MAPPER = (rs, i) ->
			new StockSector(rs.getString("STOCK_CODE"), rs.getString("SECTOR"), rs.getString("THEME"));

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** 종목코드들의 테마/섹터를 한 번에 조회한다. 테이블에 없는 종목은 결과 맵에 들어 있지 않다. */
	public Map<String, StockSector> findByCodes(Collection<String> stockCodes) {
		Map<String, StockSector> result = new HashMap<String, StockSector>();
		if (stockCodes == null || stockCodes.isEmpty()) {
			return result;
		}
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < stockCodes.size(); i++) {
			placeholders.append(i == 0 ? "?" : ",?");
		}
		List<StockSector> rows = jdbcTemplate.query(
				"SELECT STOCK_CODE, SECTOR, THEME FROM STOCK_SECTOR WHERE STOCK_CODE IN (" + placeholders + ")",
				MAPPER, stockCodes.toArray());
		for (StockSector s : rows) {
			result.put(s.getStockCode(), s);
		}
		return result;
	}

}

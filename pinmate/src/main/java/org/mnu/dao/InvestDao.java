package org.mnu.dao;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.mnu.invest.InvestResult;
import org.mnu.invest.InvestType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * INVEST_RESULT 테이블 접근. 진단할 때마다 한 행씩 쌓고, 가장 최근 행이 현재 투자성향이다.
 */
@Repository
public class InvestDao {

	private static final String COLUMNS =
			"RESULT_ID, INVEST_TYPE, SCORE_SHORT, SCORE_LONG, SCORE_DIVIDEND, SCORE_STABLE, DIAG_DATE";

	private static final RowMapper<InvestResult> MAPPER = (rs, i) -> {
		InvestResult r = new InvestResult();
		r.setResultId(rs.getLong("RESULT_ID"));
		r.setType(InvestType.valueOf(rs.getString("INVEST_TYPE")));
		Map<InvestType, Integer> scores = new EnumMap<InvestType, Integer>(InvestType.class);
		scores.put(InvestType.SHORT, rs.getInt("SCORE_SHORT"));
		scores.put(InvestType.LONG, rs.getInt("SCORE_LONG"));
		scores.put(InvestType.DIVIDEND, rs.getInt("SCORE_DIVIDEND"));
		scores.put(InvestType.STABLE, rs.getInt("SCORE_STABLE"));
		r.setScores(scores);
		r.setDiagDate(rs.getTimestamp("DIAG_DATE"));
		return r;
	};

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** 진단 결과를 저장한다. */
	public void insert(String userId, InvestType type, Map<InvestType, Integer> scores) {
		jdbcTemplate.update(
				"INSERT INTO INVEST_RESULT (RESULT_ID, USER_ID, INVEST_TYPE, SCORE_SHORT, SCORE_LONG, SCORE_DIVIDEND, SCORE_STABLE) "
				+ "VALUES (INVEST_RESULT_SEQ.NEXTVAL, ?, ?, ?, ?, ?, ?)",
				userId, type.name(), scores.get(InvestType.SHORT), scores.get(InvestType.LONG),
				scores.get(InvestType.DIVIDEND), scores.get(InvestType.STABLE));
	}

	/** 진단 이력을 최신순으로 반환한다. */
	public List<InvestResult> findByUser(String userId) {
		return jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM INVEST_RESULT WHERE USER_ID = ? ORDER BY RESULT_ID DESC",
				MAPPER, userId);
	}

}

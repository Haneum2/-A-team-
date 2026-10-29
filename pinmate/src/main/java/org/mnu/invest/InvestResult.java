package org.mnu.invest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 저장된 투자성향 진단 결과 1건.
 */
public class InvestResult {

	/** 유형별 점수 (JSP 출력용). */
	public static final class ScoreItem {
		private final InvestType type;
		private final int score;

		ScoreItem(InvestType type, int score) {
			this.type = type;
			this.score = score;
		}

		public InvestType getType() {
			return type;
		}

		public int getScore() {
			return score;
		}
	}

	private long resultId;
	private InvestType type;
	private Map<InvestType, Integer> scores;
	private Date diagDate;

	public long getResultId() {
		return resultId;
	}

	public void setResultId(long resultId) {
		this.resultId = resultId;
	}

	public InvestType getType() {
		return type;
	}

	public void setType(InvestType type) {
		this.type = type;
	}

	public void setScores(Map<InvestType, Integer> scores) {
		this.scores = scores;
	}

	public Date getDiagDate() {
		return diagDate;
	}

	public void setDiagDate(Date diagDate) {
		this.diagDate = diagDate;
	}

	public List<ScoreItem> getScoreList() {
		List<ScoreItem> list = new ArrayList<ScoreItem>();
		for (InvestType t : InvestType.values()) {
			list.add(new ScoreItem(t, scores.get(t)));
		}
		return list;
	}

	public int getTotal() {
		int sum = 0;
		for (int s : scores.values()) {
			sum += s;
		}
		return sum;
	}

}

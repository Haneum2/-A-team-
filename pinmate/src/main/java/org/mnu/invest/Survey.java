package org.mnu.invest;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;

/**
 * 투자성향 진단 설문. 문항마다 보기가 유형 하나에 대응하며, 선택된 보기의 유형에 1점씩 더해
 * 가장 높은 점수의 유형을 투자성향으로 분류한다.
 */
public final class Survey {

	/** 보기 하나. 선택하면 type 에 1점이 더해진다. */
	public static final class Option {
		private final InvestType type;
		private final String text;

		Option(InvestType type, String text) {
			this.type = type;
			this.text = text;
		}

		public InvestType getType() {
			return type;
		}

		public String getText() {
			return text;
		}
	}

	public static final class Question {
		private final String id;
		private final String text;
		private final List<Option> options;

		Question(String id, String text, Option... options) {
			this.id = id;
			this.text = text;
			this.options = Collections.unmodifiableList(Arrays.asList(options));
		}

		public String getId() {
			return id;
		}

		public String getText() {
			return text;
		}

		public List<Option> getOptions() {
			return options;
		}
	}

	private static final List<Question> QUESTIONS = Collections.unmodifiableList(Arrays.asList(
		new Question("q1", "주식 투자를 하는 가장 큰 목적은 무엇인가요?",
			new Option(InvestType.SHORT, "단기간에 빠르게 수익을 내는 것"),
			new Option(InvestType.LONG, "시간을 두고 자산을 크게 불리는 것"),
			new Option(InvestType.DIVIDEND, "배당 등 정기적인 현금 흐름을 만드는 것"),
			new Option(InvestType.STABLE, "원금을 지키면서 예금보다 조금 더 버는 것")),
		new Question("q2", "한 종목을 보통 얼마나 보유하고 싶으신가요?",
			new Option(InvestType.DIVIDEND, "배당을 꾸준히 받을 수 있다면 오래 보유"),
			new Option(InvestType.SHORT, "며칠에서 몇 주 이내"),
			new Option(InvestType.STABLE, "크게 오르지 않아도 안정적이면 계속 보유"),
			new Option(InvestType.LONG, "최소 몇 년 이상")),
		new Question("q3", "보유 종목이 한 달 만에 20% 하락했다면 어떻게 하시겠어요?",
			new Option(InvestType.LONG, "기업 가치가 그대로라면 추가 매수한다"),
			new Option(InvestType.STABLE, "불안해서 손실을 줄이고 정리한다"),
			new Option(InvestType.SHORT, "추세가 꺾였다고 보고 바로 손절한다"),
			new Option(InvestType.DIVIDEND, "배당이 유지되는 한 그대로 보유한다")),
		new Question("q4", "어떤 방식의 수익이 가장 매력적인가요?",
			new Option(InvestType.STABLE, "작더라도 손실 가능성이 낮은 수익"),
			new Option(InvestType.SHORT, "짧은 시간에 얻는 큰 시세 차익"),
			new Option(InvestType.DIVIDEND, "분기·연 단위로 받는 배당금"),
			new Option(InvestType.LONG, "오랜 기간 복리로 쌓이는 주가 상승")),
		new Question("q5", "투자 종목의 시세를 얼마나 자주 확인하나요?",
			new Option(InvestType.SHORT, "장중에 수시로 확인한다"),
			new Option(InvestType.DIVIDEND, "배당·실적 발표 시기에 확인한다"),
			new Option(InvestType.LONG, "분기나 반기에 한 번 정도 확인한다"),
			new Option(InvestType.STABLE, "자주 보면 불안해서 거의 보지 않는다")),
		new Question("q6", "가장 관심이 가는 종목은 어떤 유형인가요?",
			new Option(InvestType.LONG, "꾸준히 성장하는 우량 기업"),
			new Option(InvestType.SHORT, "최근 이슈나 테마로 주목받는 종목"),
			new Option(InvestType.STABLE, "변동성이 낮은 대형 우량주·채권형 상품"),
			new Option(InvestType.DIVIDEND, "배당 수익률이 높은 종목")),
		new Question("q7", "투자 자금에서 감내할 수 있는 최대 손실은 어느 정도인가요?",
			new Option(InvestType.DIVIDEND, "10% 이내"),
			new Option(InvestType.STABLE, "거의 없어야 한다"),
			new Option(InvestType.SHORT, "30% 이상도 감수할 수 있다"),
			new Option(InvestType.LONG, "단기 하락은 20~30%까지 견딜 수 있다"))
	));

	private Survey() {
	}

	public static List<Question> getQuestions() {
		return QUESTIONS;
	}

	/**
	 * 요청 파라미터(q1~q7)의 응답을 채점한다. 모든 문항에 유효한 답이 없으면 null.
	 */
	public static Map<InvestType, Integer> score(ServletRequest request) {
		Map<InvestType, Integer> scores = new EnumMap<InvestType, Integer>(InvestType.class);
		for (InvestType t : InvestType.values()) {
			scores.put(t, 0);
		}
		for (Question q : QUESTIONS) {
			String value = request.getParameter(q.getId());
			InvestType picked = null;
			for (Option o : q.getOptions()) {
				if (o.getType().name().equals(value)) {
					picked = o.getType();
				}
			}
			if (picked == null) {
				return null;
			}
			scores.put(picked, scores.get(picked) + 1);
		}
		return scores;
	}

	/** 최고 점수 유형을 반환한다. 동점이면 더 보수적인(enum 뒤쪽) 유형. */
	public static InvestType decide(Map<InvestType, Integer> scores) {
		InvestType best = null;
		for (InvestType t : InvestType.values()) {
			if (best == null || scores.get(t) >= scores.get(best)) {
				best = t;
			}
		}
		return best;
	}

}

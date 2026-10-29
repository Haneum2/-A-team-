package org.mnu.invest;

/**
 * 투자성향 유형. 선언 순서는 보수적일수록 뒤쪽이며, 동점일 때는 뒤쪽(보수적인) 유형이 선택된다.
 */
public enum InvestType {

	SHORT("단타형", "단기 시세 차익을 노리며 시장 변화에 빠르게 대응하는 적극적인 투자자입니다."),
	LONG("장투형", "좋은 기업을 오래 보유하며 장기적인 성장에 투자하는 투자자입니다."),
	DIVIDEND("배당형", "배당 등 꾸준한 현금 흐름을 중시하는 투자자입니다."),
	STABLE("안정형", "원금 보전을 최우선으로 하며 변동성이 낮은 자산을 선호하는 투자자입니다.");

	private final String label;
	private final String description;

	InvestType(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

}

package org.mnu.portfolio;

/** 사용자에게 그대로 보여 줄 수 있는 메시지를 담은 포트폴리오 업무 오류 (입력 오류, 중복 등). */
public class PortfolioException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PortfolioException(String userMessage) {
		super(userMessage);
	}
}

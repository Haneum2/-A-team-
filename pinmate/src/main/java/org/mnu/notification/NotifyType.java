package org.mnu.notification;

/** 알림 종류 (SFR-010). 회원은 종류별로 수신 여부를 설정한다. */
public enum NotifyType {
	NEWS("뉴스", "NEWS_YN"),
	PRICE("급등락", "PRICE_YN"),
	REPORT("리포트", "REPORT_YN");

	private final String label;
	/** NOTIFY_SETTING 테이블에서 이 종류의 수신 여부 컬럼 */
	private final String settingColumn;

	NotifyType(String label, String settingColumn) {
		this.label = label;
		this.settingColumn = settingColumn;
	}

	public String getLabel() { return label; }
	public String getSettingColumn() { return settingColumn; }
}

package org.mnu.notification;

/** 회원의 알림 수신 설정 (UC-010). 설정한 적이 없으면 모두 받는 것(true)이 기본값이다. */
public class NotifySetting {

	private boolean news = true;
	private boolean price = true;
	private boolean report = true;

	public NotifySetting() {
	}

	public NotifySetting(boolean news, boolean price, boolean report) {
		this.news = news;
		this.price = price;
		this.report = report;
	}

	public boolean isEnabled(NotifyType type) {
		switch (type) {
		case NEWS:
			return news;
		case PRICE:
			return price;
		case REPORT:
			return report;
		default:
			return false;
		}
	}

	public boolean isNews() { return news; }
	public void setNews(boolean news) { this.news = news; }
	public boolean isPrice() { return price; }
	public void setPrice(boolean price) { this.price = price; }
	public boolean isReport() { return report; }
	public void setReport(boolean report) { this.report = report; }
}

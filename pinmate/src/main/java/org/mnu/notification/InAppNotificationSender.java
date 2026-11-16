package org.mnu.notification;

import org.springframework.stereotype.Component;

/**
 * 앱 내 알림. 알림 행 자체가 알림함에 보이므로 따로 보낼 것이 없다.
 * NotificationService 가 이 메서드가 끝나면 STATUS 를 SENT 로 바꾸고, 그때부터 회원의 알림함과 상단 배지에 나타난다.
 */
@Component
public class InAppNotificationSender implements NotificationSender {

	@Override
	public void send(Notification notification) {
		// 앱 내 알림은 DB 에 SENT 로 기록되는 것이 곧 발송이다.
	}
}

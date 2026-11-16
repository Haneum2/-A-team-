package org.mnu.notification;

/**
 * 알림을 실제로 회원에게 전달하는 방법. 지금은 앱 내 알림(InAppNotificationSender)만 있다.
 * 웹 푸시(FCM)나 이메일을 붙일 때는 이 인터페이스를 구현한 클래스를 추가하면 된다.
 * 전달에 실패하면 예외를 던진다 → 이력에 FAILED 로 남고 다음 주기에 다시 보낸다.
 */
public interface NotificationSender {

	void send(Notification notification) throws Exception;
}

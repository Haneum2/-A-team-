package org.mnu.notification;

import java.util.List;

import org.mnu.dao.NotificationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 알림 기록·발송·재발송과 알림함/수신 설정 처리.
 *
 * 발송 흐름: PENDING 으로 기록 → (수신 설정이 꺼져 있으면 CANCELED) → sender.send → SENT
 *                                                      └ 실패하면 FAILED (RETRY_COUNT+1) → 다음 주기에 재발송 (최대 MAX_RETRY 회)
 */
@Service
public class NotificationService {

	private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

	/** 이 횟수만큼 실패하면 더 이상 재발송하지 않는다. */
	public static final int MAX_RETRY = 3;
	private static final int RETRY_BATCH = 100;
	private static final int INBOX_SIZE = 50;

	// 컬럼 길이(바이트)를 넘지 않도록 한글 3바이트 기준으로 잡은 글자 수
	private static final int TITLE_MAX = 130;
	private static final int CONTENT_MAX = 1300;
	private static final int LINK_MAX = 2000;
	private static final int REASON_MAX = 100;

	@Autowired
	NotificationDao notificationDao;

	@Autowired
	NotificationSender sender;

	// ---- 발송 (UC-011) ----

	/**
	 * 알림을 기록하고 바로 발송한다.
	 * @return 새로 기록했으면 true, 같은 dedupKey 로 이미 보낸 알림이면 false (중복 발송 방지)
	 */
	public boolean publish(String userId, NotifyType type, String stockCode, String title, String content,
			String link, String dedupKey) {
		Notification n = new Notification();
		n.setUserId(userId);
		n.setType(type);
		n.setStockCode(stockCode);
		n.setTitle(NotificationRules.truncate(title, TITLE_MAX));
		n.setContent(NotificationRules.truncate(content, CONTENT_MAX));
		n.setLink(link != null && link.length() <= LINK_MAX ? link : null);
		n.setDedupKey(dedupKey);
		n.setNotiId(notificationDao.nextId());
		try {
			notificationDao.insertPending(n);
		} catch (DuplicateKeyException e) {
			return false;
		}
		deliver(n);
		return true;
	}

	/** 실패했거나 PENDING 에 멈춘 알림을 다시 보낸다. 다시 보낸 건수를 돌려준다. */
	public int retryFailed() {
		List<Notification> list = notificationDao.findRetryable(MAX_RETRY, RETRY_BATCH);
		for (Notification n : list) {
			deliver(n);
		}
		return list.size();
	}

	/** 보내기 직전에 수신 설정을 한 번 더 확인한다. (기록 후 재발송 사이에 회원이 끈 경우 포함) */
	void deliver(Notification n) {
		if (!notificationDao.findSetting(n.getUserId()).isEnabled(n.getType())) {
			notificationDao.markCanceled(n.getNotiId());
			return;
		}
		try {
			sender.send(n);
			notificationDao.markSent(n.getNotiId());
		} catch (Exception e) {
			logger.warn("알림 발송 실패: notiId=" + n.getNotiId() + ", " + e);
			notificationDao.markFailed(n.getNotiId(), NotificationRules.truncate(e.toString(), REASON_MAX));
		}
	}

	// ---- 알림함 ----

	public List<Notification> getInbox(String userId) {
		return notificationDao.findInbox(userId, INBOX_SIZE);
	}

	public int countUnread(String userId) {
		return notificationDao.countUnread(userId);
	}

	public void markAllRead(String userId) {
		notificationDao.markAllRead(userId);
	}

	/** 본인 알림을 읽음 처리하고 이동할 링크를 돌려준다. 남의 알림이거나 없으면 null. */
	public Notification open(String userId, long notiId) {
		Notification n = notificationDao.findSent(userId, notiId);
		if (n != null && !n.isRead()) {
			notificationDao.markRead(userId, notiId);
			n.setRead(true);
		}
		return n;
	}

	// ---- 수신 설정 (UC-010) ----

	public NotifySetting getSetting(String userId) {
		return notificationDao.findSetting(userId);
	}

	public void saveSetting(String userId, NotifySetting setting) {
		notificationDao.saveSetting(userId, setting);
	}
}

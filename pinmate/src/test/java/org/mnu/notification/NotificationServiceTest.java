package org.mnu.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mnu.dao.NotificationDao;
import org.springframework.dao.DuplicateKeyException;

/** DB 없이 메모리 DAO 로 발송·중복 방지·수신 설정·재발송 흐름을 확인한다. */
public class NotificationServiceTest {

	private FakeDao dao;
	private FakeSender sender;
	private NotificationService service;

	@Before
	public void setUp() {
		dao = new FakeDao();
		sender = new FakeSender();
		service = new NotificationService();
		service.notificationDao = dao;
		service.sender = sender;
	}

	private boolean publishNews(String userId, String link) {
		return service.publish(userId, NotifyType.NEWS, "005930", "[삼성전자] 기사", "한국경제", link,
				NotificationRules.newsKey(link));
	}

	@Test
	public void 발송하면_SENT_로_알림함에_보인다() {
		assertTrue(publishNews("u1", "https://a"));
		assertEquals(1, sender.sent.size());
		assertEquals(1, service.getInbox("u1").size());
		assertEquals(1, service.countUnread("u1"));
		assertEquals(0, service.getInbox("u2").size()); // 다른 회원에게는 안 보임
	}

	@Test
	public void 같은_기사는_두번_보내지_않는다() {
		assertTrue(publishNews("u1", "https://a"));
		assertFalse(publishNews("u1", "https://a"));
		assertEquals(1, sender.sent.size());
		assertTrue(publishNews("u2", "https://a")); // 다른 회원에게는 보냄
	}

	@Test
	public void 수신_설정을_끄면_보내지_않는다() {
		service.saveSetting("u1", new NotifySetting(false, true, true));
		publishNews("u1", "https://a");
		assertEquals(0, sender.sent.size());
		assertEquals(0, service.getInbox("u1").size());
		assertEquals(Notification.CANCELED, dao.rows.values().iterator().next().getStatus());

		// 다른 종류는 그대로 받는다
		assertTrue(service.publish("u1", NotifyType.PRICE, "005930", "급등", null, null, "PRICE:1"));
		assertEquals(1, service.getInbox("u1").size());
	}

	@Test
	public void 설정을_저장한_적_없으면_모두_받는다() {
		NotifySetting s = service.getSetting("new-user");
		assertTrue(s.isNews() && s.isPrice() && s.isReport());
	}

	@Test
	public void 실패하면_기록하고_다음_주기에_재발송() {
		sender.failNext = 1;
		publishNews("u1", "https://a");
		Notification n = dao.rows.values().iterator().next();
		assertEquals(Notification.FAILED, n.getStatus());
		assertEquals(1, n.getRetryCount());
		assertEquals(0, service.getInbox("u1").size());

		assertEquals(1, service.retryFailed());
		assertEquals(Notification.SENT, n.getStatus());
		assertEquals(1, service.getInbox("u1").size());
		assertEquals(0, service.retryFailed()); // 성공한 건 다시 보내지 않음
	}

	@Test
	public void 최대_횟수만큼_실패하면_재발송을_멈춘다() {
		sender.failNext = 100;
		publishNews("u1", "https://a");
		for (int i = 0; i < 10; i++) {
			service.retryFailed();
		}
		Notification n = dao.rows.values().iterator().next();
		assertEquals(Notification.FAILED, n.getStatus());
		assertEquals(NotificationService.MAX_RETRY, n.getRetryCount());
	}

	@Test
	public void 재발송_전에_설정을_끄면_취소() {
		sender.failNext = 1;
		publishNews("u1", "https://a");
		service.saveSetting("u1", new NotifySetting(false, true, true));
		service.retryFailed();
		assertEquals(Notification.CANCELED, dao.rows.values().iterator().next().getStatus());
		assertEquals(0, service.retryFailed());
	}

	@Test
	public void 남의_알림은_열_수_없고_열면_읽음() {
		publishNews("u1", "https://a");
		long id = dao.rows.keySet().iterator().next();
		assertNull(service.open("u2", id));
		assertEquals(1, service.countUnread("u1"));
		assertEquals("https://a", service.open("u1", id).getLink());
		assertEquals(0, service.countUnread("u1"));
	}

	// ---- 테스트용 가짜 ----

	static class FakeSender implements NotificationSender {
		final List<Notification> sent = new ArrayList<Notification>();
		int failNext;

		@Override
		public void send(Notification n) throws Exception {
			if (failNext > 0) {
				failNext--;
				throw new Exception("push server down");
			}
			sent.add(n);
		}
	}

	static class FakeDao extends NotificationDao {
		final Map<Long, Notification> rows = new LinkedHashMap<Long, Notification>();
		final Map<String, NotifySetting> settings = new HashMap<String, NotifySetting>();
		long seq;

		@Override
		public NotifySetting findSetting(String userId) {
			NotifySetting s = settings.get(userId);
			return s != null ? s : new NotifySetting();
		}

		@Override
		public void saveSetting(String userId, NotifySetting s) {
			settings.put(userId, s);
		}

		@Override
		public long nextId() {
			return ++seq;
		}

		@Override
		public void insertPending(Notification n) {
			for (Notification r : rows.values()) {
				if (r.getUserId().equals(n.getUserId()) && r.getDedupKey().equals(n.getDedupKey())) {
					throw new DuplicateKeyException("UQ_NOTIFICATION_DEDUP");
				}
			}
			n.setStatus(Notification.PENDING);
			rows.put(n.getNotiId(), n);
		}

		@Override
		public void markSent(long id) {
			rows.get(id).setStatus(Notification.SENT);
		}

		@Override
		public void markFailed(long id, String reason) {
			Notification n = rows.get(id);
			n.setStatus(Notification.FAILED);
			n.setRetryCount(n.getRetryCount() + 1);
		}

		@Override
		public void markCanceled(long id) {
			rows.get(id).setStatus(Notification.CANCELED);
		}

		@Override
		public List<Notification> findRetryable(int maxRetry, int limit) {
			List<Notification> out = new ArrayList<Notification>();
			for (Notification n : rows.values()) {
				if (Notification.FAILED.equals(n.getStatus()) && n.getRetryCount() < maxRetry) {
					out.add(n);
				}
			}
			return out;
		}

		@Override
		public List<Notification> findInbox(String userId, int limit) {
			List<Notification> out = new ArrayList<Notification>();
			for (Notification n : rows.values()) {
				if (n.getUserId().equals(userId) && Notification.SENT.equals(n.getStatus())) {
					out.add(0, n);
				}
			}
			return out;
		}

		@Override
		public Notification findSent(String userId, long id) {
			Notification n = rows.get(id);
			return n != null && n.getUserId().equals(userId) && Notification.SENT.equals(n.getStatus()) ? n : null;
		}

		@Override
		public int countUnread(String userId) {
			int c = 0;
			for (Notification n : findInbox(userId, 50)) {
				if (!n.isRead()) {
					c++;
				}
			}
			return c;
		}

		@Override
		public void markRead(String userId, long id) {
			rows.get(id).setRead(true);
		}
	}
}

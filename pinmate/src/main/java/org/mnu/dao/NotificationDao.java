package org.mnu.dao;

import java.util.List;

import org.mnu.notification.Notification;
import org.mnu.notification.NotifySetting;
import org.mnu.notification.NotifyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * NOTIFY_SETTING(수신 설정), NOTIFICATION(알림함 겸 발송 이력) 테이블 접근.
 * 회원 화면에서 쓰는 조회·수정에는 모두 USER_ID 조건을 걸어 다른 회원의 알림은 건드릴 수 없게 한다.
 * 같은 (USER_ID, DEDUP_KEY) 를 두 번 넣으면 유니크 제약 때문에 DuplicateKeyException 이 난다 (= 이미 보낸 알림).
 */
@Repository
public class NotificationDao {

	private static final String COLUMNS = "NOTI_ID, USER_ID, NOTI_TYPE, STOCK_CODE, TITLE, CONTENT, LINK, "
			+ "DEDUP_KEY, STATUS, RETRY_COUNT, READ_YN, CREATED_AT, SENT_AT";

	/** PENDING 상태로 이 시간(분) 넘게 남아 있으면 발송 도중 서버가 멈춘 것으로 보고 다시 보낸다. */
	private static final int STUCK_PENDING_MINUTES = 10;

	private static final RowMapper<Notification> MAPPER = (rs, i) -> {
		Notification n = new Notification();
		n.setNotiId(rs.getLong("NOTI_ID"));
		n.setUserId(rs.getString("USER_ID"));
		n.setType(NotifyType.valueOf(rs.getString("NOTI_TYPE")));
		n.setStockCode(rs.getString("STOCK_CODE"));
		n.setTitle(rs.getString("TITLE"));
		n.setContent(rs.getString("CONTENT"));
		n.setLink(rs.getString("LINK"));
		n.setDedupKey(rs.getString("DEDUP_KEY"));
		n.setStatus(rs.getString("STATUS"));
		n.setRetryCount(rs.getInt("RETRY_COUNT"));
		n.setRead("Y".equals(rs.getString("READ_YN")));
		n.setCreatedAt(rs.getTimestamp("CREATED_AT"));
		n.setSentAt(rs.getTimestamp("SENT_AT"));
		return n;
	};

	private static final RowMapper<NotifySetting> SETTING_MAPPER = (rs, i) -> new NotifySetting(
			"Y".equals(rs.getString("NEWS_YN")),
			"Y".equals(rs.getString("PRICE_YN")),
			"Y".equals(rs.getString("REPORT_YN")));

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ---- 수신 설정 (UC-010) ----

	/** 회원의 수신 설정. 저장한 적이 없으면 기본값(모두 수신). */
	public NotifySetting findSetting(String userId) {
		List<NotifySetting> list = jdbcTemplate.query(
				"SELECT NEWS_YN, PRICE_YN, REPORT_YN FROM NOTIFY_SETTING WHERE USER_ID = ?",
				SETTING_MAPPER, userId);
		return list.isEmpty() ? new NotifySetting() : list.get(0);
	}

	public void saveSetting(String userId, NotifySetting s) {
		String news = yn(s.isNews()), price = yn(s.isPrice()), report = yn(s.isReport());
		jdbcTemplate.update(
				"MERGE INTO NOTIFY_SETTING t USING (SELECT ? AS USER_ID FROM DUAL) src ON (t.USER_ID = src.USER_ID) "
				+ "WHEN MATCHED THEN UPDATE SET NEWS_YN = ?, PRICE_YN = ?, REPORT_YN = ?, UPD_DATE = SYSDATE "
				+ "WHEN NOT MATCHED THEN INSERT (USER_ID, NEWS_YN, PRICE_YN, REPORT_YN) VALUES (?, ?, ?, ?)",
				userId, news, price, report, userId, news, price, report);
	}

	/** 보유 종목이 있고, 해당 종류의 알림을 받는 회원들. (설정 행이 없으면 받는 것으로 본다) */
	public List<String> findSubscribersWithHoldings(NotifyType type) {
		// 컬럼명은 enum 상수에서만 오므로 문자열로 붙여도 안전하다.
		return jdbcTemplate.queryForList(
				"SELECT DISTINCT p.USER_ID FROM PORTFOLIO p LEFT JOIN NOTIFY_SETTING s ON s.USER_ID = p.USER_ID "
				+ "WHERE NVL(s." + type.getSettingColumn() + ", 'Y') = 'Y' ORDER BY p.USER_ID",
				String.class);
	}

	// ---- 발송 이력 (UC-011) ----

	public long nextId() {
		return jdbcTemplate.queryForObject("SELECT NOTIFICATION_SEQ.NEXTVAL FROM DUAL", Long.class);
	}

	/** PENDING 상태로 기록한다. 같은 dedupKey 가 이미 있으면 DuplicateKeyException. */
	public void insertPending(Notification n) {
		jdbcTemplate.update(
				"INSERT INTO NOTIFICATION (NOTI_ID, USER_ID, NOTI_TYPE, STOCK_CODE, TITLE, CONTENT, LINK, DEDUP_KEY, STATUS) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')",
				n.getNotiId(), n.getUserId(), n.getType().name(), n.getStockCode(), n.getTitle(),
				n.getContent(), n.getLink(), n.getDedupKey());
	}

	public void markSent(long notiId) {
		jdbcTemplate.update(
				"UPDATE NOTIFICATION SET STATUS = 'SENT', SENT_AT = SYSDATE, FAIL_REASON = NULL WHERE NOTI_ID = ?",
				notiId);
	}

	public void markFailed(long notiId, String reason) {
		jdbcTemplate.update(
				"UPDATE NOTIFICATION SET STATUS = 'FAILED', RETRY_COUNT = RETRY_COUNT + 1, FAIL_REASON = ? WHERE NOTI_ID = ?",
				reason, notiId);
	}

	public void markCanceled(long notiId) {
		jdbcTemplate.update("UPDATE NOTIFICATION SET STATUS = 'CANCELED' WHERE NOTI_ID = ?", notiId);
	}

	/** 다시 보낼 알림: 실패 횟수가 maxRetry 미만인 FAILED, 또는 오래 PENDING 에 멈춘 것. 오래된 순. */
	public List<Notification> findRetryable(int maxRetry, int limit) {
		return jdbcTemplate.query(
				"SELECT * FROM (SELECT " + COLUMNS + " FROM NOTIFICATION "
				+ "WHERE (STATUS = 'FAILED' AND RETRY_COUNT < ?) "
				+ "OR (STATUS = 'PENDING' AND CREATED_AT < SYSDATE - ? / 1440) "
				+ "ORDER BY NOTI_ID) WHERE ROWNUM <= ?",
				MAPPER, maxRetry, STUCK_PENDING_MINUTES, limit);
	}

	// ---- 알림함 ----

	/** 회원에게 발송된 알림을 최신순으로 limit 건. */
	public List<Notification> findInbox(String userId, int limit) {
		return jdbcTemplate.query(
				"SELECT * FROM (SELECT " + COLUMNS + " FROM NOTIFICATION "
				+ "WHERE USER_ID = ? AND STATUS = 'SENT' ORDER BY NOTI_ID DESC) WHERE ROWNUM <= ?",
				MAPPER, userId, limit);
	}

	/** 회원 본인의 발송된 알림 한 건. 없으면 null. */
	public Notification findSent(String userId, long notiId) {
		List<Notification> list = jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM NOTIFICATION WHERE NOTI_ID = ? AND USER_ID = ? AND STATUS = 'SENT'",
				MAPPER, notiId, userId);
		return list.isEmpty() ? null : list.get(0);
	}

	public int countUnread(String userId) {
		Integer n = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM NOTIFICATION WHERE USER_ID = ? AND STATUS = 'SENT' AND READ_YN = 'N'",
				Integer.class, userId);
		return n == null ? 0 : n;
	}

	public void markRead(String userId, long notiId) {
		jdbcTemplate.update("UPDATE NOTIFICATION SET READ_YN = 'Y' WHERE NOTI_ID = ? AND USER_ID = ?", notiId, userId);
	}

	public void markAllRead(String userId) {
		jdbcTemplate.update(
				"UPDATE NOTIFICATION SET READ_YN = 'Y' WHERE USER_ID = ? AND STATUS = 'SENT' AND READ_YN = 'N'", userId);
	}

	private static String yn(boolean b) {
		return b ? "Y" : "N";
	}
}

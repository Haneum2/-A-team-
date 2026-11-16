package org.mnu.notification;

import java.util.Date;

/** NOTIFICATION 테이블 한 행. 알림함에 보이는 알림이자 발송 이력이다. */
public class Notification {

	public static final String PENDING = "PENDING";
	public static final String SENT = "SENT";
	public static final String FAILED = "FAILED";
	/** 발송 전에 회원이 해당 알림 수신을 끈 경우 */
	public static final String CANCELED = "CANCELED";

	private long notiId;
	private String userId;
	private NotifyType type;
	private String stockCode;
	private String title;
	private String content;
	private String link;
	private String dedupKey;
	private String status = PENDING;
	private int retryCount;
	private boolean read;
	private Date createdAt;
	private Date sentAt;

	/** 링크가 이 사이트 안의 경로인지 (/portfolio 등). JSP 에서 contextPath 를 붙일지 정할 때 쓴다. */
	public boolean isInternalLink() {
		return link != null && link.startsWith("/") && !link.startsWith("//");
	}

	public long getNotiId() { return notiId; }
	public void setNotiId(long notiId) { this.notiId = notiId; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public NotifyType getType() { return type; }
	public void setType(NotifyType type) { this.type = type; }
	public String getStockCode() { return stockCode; }
	public void setStockCode(String stockCode) { this.stockCode = stockCode; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }
	public String getLink() { return link; }
	public void setLink(String link) { this.link = link; }
	public String getDedupKey() { return dedupKey; }
	public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public int getRetryCount() { return retryCount; }
	public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
	public boolean isRead() { return read; }
	public void setRead(boolean read) { this.read = read; }
	public Date getCreatedAt() { return createdAt; }
	public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
	public Date getSentAt() { return sentAt; }
	public void setSentAt(Date sentAt) { this.sentAt = sentAt; }
}

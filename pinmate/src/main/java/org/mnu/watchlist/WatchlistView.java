package org.mnu.watchlist;

import java.util.List;

/** 관심종목 화면에 보여줄 목록과 시세 상태. */
public class WatchlistView {

	private final List<WatchItem> items;
	private final boolean stale;
	private final String errorMsg;
	private final String asOfText;

	public WatchlistView(List<WatchItem> items, boolean stale, String errorMsg, String asOfText) {
		this.items = items;
		this.stale = stale;
		this.errorMsg = errorMsg;
		this.asOfText = asOfText;
	}

	public List<WatchItem> getItems() { return items; }

	/** 관심종목이 하나도 없는가. (EL 예약어 empty 와 겹치지 않게 noItems 로 쓴다) */
	public boolean isNoItems() { return items.isEmpty(); }

	/** 시세 서버 장애로 마지막으로 받은 값을 보여주는 중인가 */
	public boolean isStale() { return stale; }

	/** 시세 조회 오류 메시지 (없으면 null) */
	public String getErrorMsg() { return errorMsg; }

	/** 시세 기준 시각 (없으면 null) */
	public String getAsOfText() { return asOfText; }
}

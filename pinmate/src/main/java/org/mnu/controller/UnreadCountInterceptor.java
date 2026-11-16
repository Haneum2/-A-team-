package org.mnu.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.mnu.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 로그인한 회원이 화면을 볼 때 안 읽은 알림 수(unreadNotiCount)를 모델에 넣어서 상단 nav 의 알림 배지에 쓴다.
 * servlet-context.xml 의 interceptors 에 등록한다. (@Component 를 붙이면 component-scan 으로 한 번 더 만들어지므로 붙이지 않는다)
 * redirect 응답에는 넣지 않는다. (모델 값이 redirect URL 의 쿼리 파라미터로 붙기 때문)
 */
public class UnreadCountInterceptor implements HandlerInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(UnreadCountInterceptor.class);

	@Autowired
	private NotificationService notificationService;

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView mav) {
		if (mav == null || mav.getViewName() == null || mav.getViewName().startsWith("redirect:")) {
			return;
		}
		HttpSession session = request.getSession(false);
		String userId = session != null ? (String) session.getAttribute("loginUser") : null;
		if (userId == null) {
			return;
		}
		try {
			mav.addObject("unreadNotiCount", notificationService.countUnread(userId));
		} catch (Exception e) {
			// 배지 때문에 화면 전체가 실패하면 안 되므로 배지만 생략한다.
			logger.warn("안 읽은 알림 수 조회 실패: " + e);
		}
	}
}

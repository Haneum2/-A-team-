package org.mnu.controller;

import javax.servlet.http.HttpSession;

import org.mnu.notification.Notification;
import org.mnu.notification.NotificationService;
import org.mnu.notification.NotifySetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 알림함(UC-011 결과 확인)과 알림 설정(UC-010).
 * 변경은 POST 로 처리한 뒤 redirect 한다. (새로고침해도 다시 전송되지 않게)
 */
@Controller
public class NotificationController {

	private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

	@Autowired
	private NotificationService notificationService;

	@RequestMapping(value = "/notifications", method = RequestMethod.GET)
	public String inbox(HttpSession session, Model model) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			model.addAttribute("items", notificationService.getInbox(userId));
		} catch (DataAccessException e) {
			logger.error("Load notifications error", e);
			model.addAttribute("error", LoginController.DB_ERROR_MSG);
		}
		return "notifications";
	}

	@RequestMapping(value = "/notifications/read-all", method = RequestMethod.POST)
	public String readAll(HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			notificationService.markAllRead(userId);
		} catch (DataAccessException e) {
			logger.error("Mark all read error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
		}
		return "redirect:/notifications";
	}

	/** 알림을 누르면 읽음 처리하고 알림의 링크(기사, 종목 상세, 포트폴리오 등)로 보낸다. */
	@RequestMapping(value = "/notifications/{notiId}/open", method = RequestMethod.GET)
	public String open(@PathVariable("notiId") long notiId, HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		Notification n;
		try {
			n = notificationService.open(userId, notiId);
		} catch (DataAccessException e) {
			logger.error("Open notification error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
			return "redirect:/notifications";
		}
		if (n == null) {
			redirect.addFlashAttribute("flashError", "알림을 찾을 수 없습니다.");
			return "redirect:/notifications";
		}
		String link = n.getLink();
		// 사이트 내부 경로이거나 http(s) 주소일 때만 이동한다. (javascript: 등 차단)
		if (n.isInternalLink() || (link != null && (link.startsWith("https://") || link.startsWith("http://")))) {
			return "redirect:" + link;
		}
		return "redirect:/notifications";
	}

	@RequestMapping(value = "/notifications/settings", method = RequestMethod.GET)
	public String settings(HttpSession session, Model model) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			model.addAttribute("setting", notificationService.getSetting(userId));
		} catch (DataAccessException e) {
			logger.error("Load notify setting error", e);
			model.addAttribute("error", LoginController.DB_ERROR_MSG);
		}
		return "notification-settings";
	}

	/** 체크박스는 켜진 것만 전송되므로, 값이 없으면 끈 것으로 본다. */
	@RequestMapping(value = "/notifications/settings", method = RequestMethod.POST)
	public String saveSettings(@RequestParam(value = "news", required = false) String news,
			@RequestParam(value = "price", required = false) String price,
			@RequestParam(value = "report", required = false) String report,
			HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			notificationService.saveSetting(userId, new NotifySetting("Y".equals(news), "Y".equals(price), "Y".equals(report)));
			redirect.addFlashAttribute("flashMsg", "알림 설정을 저장했습니다.");
		} catch (DataAccessException e) {
			logger.error("Save notify setting error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
		}
		return "redirect:/notifications/settings";
	}
}

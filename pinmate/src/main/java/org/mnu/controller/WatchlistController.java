package org.mnu.controller;

import javax.servlet.http.HttpSession;

import org.mnu.disclosure.CorpInfo;
import org.mnu.disclosure.DisclosureService;
import org.mnu.watchlist.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관심종목 (종목 상세에서 추가 → 관심종목 화면에서 현재가·등락률 확인 → 삭제).
 * 추가·삭제는 POST 로 처리한 뒤 redirect 한다. (새로고침해도 다시 전송되지 않게)
 */
@Controller
public class WatchlistController {

	private static final Logger logger = LoggerFactory.getLogger(WatchlistController.class);

	static final String DUPLICATE_MSG = "이미 관심종목에 등록된 종목입니다.";

	@Autowired
	private WatchlistService watchlistService;

	@Autowired
	private DisclosureService disclosureService;

	@RequestMapping(value = "/watchlist", method = RequestMethod.GET)
	public String list(HttpSession session, Model model) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			model.addAttribute("view", watchlistService.getView(userId));
		} catch (DataAccessException e) {
			logger.error("Load watchlist error", e);
			model.addAttribute("error", LoginController.DB_ERROR_MSG);
		}
		return "watchlist";
	}

	/** 종목 상세 화면의 '관심종목 추가'. 종목명은 클라이언트 값을 믿지 않고 DART 종목 목록에서 다시 찾는다. */
	@RequestMapping(value = "/watchlist/add", method = RequestMethod.POST)
	public String add(@RequestParam(value = "stockCode", defaultValue = "") String stockCode,
			HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		String code = stockCode.trim();
		CorpInfo corp = disclosureService.isApiKeyConfigured() ? findByStockCode(code) : null;
		if (corp == null) {
			redirect.addFlashAttribute("flashError", "종목 정보를 찾을 수 없어 관심종목에 추가하지 못했습니다.");
			return "redirect:/stock/search";
		}
		try {
			watchlistService.add(userId, corp.getStockCode(), corp.getCorpName());
			redirect.addFlashAttribute("flashMsg", corp.getCorpName() + " 종목을 관심종목에 추가했습니다.");
		} catch (DuplicateKeyException e) {
			redirect.addFlashAttribute("flashError", DUPLICATE_MSG);
		} catch (DataAccessException e) {
			logger.error("Add watchlist error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
		}
		return "redirect:/stock/" + corp.getStockCode();
	}

	@RequestMapping(value = "/watchlist/delete", method = RequestMethod.POST)
	public String delete(@RequestParam("watchId") long watchId, HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			if (watchlistService.delete(userId, watchId)) {
				redirect.addFlashAttribute("flashMsg", "관심종목에서 삭제했습니다.");
			} else {
				redirect.addFlashAttribute("flashError", "삭제할 종목을 찾을 수 없습니다.");
			}
		} catch (DataAccessException e) {
			logger.error("Delete watchlist error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
		}
		return "redirect:/watchlist";
	}

	/** searchByName 은 종목코드 완전일치도 함께 찾으므로, 그 중 코드가 같은 항목만 고른다. */
	private CorpInfo findByStockCode(String stockCode) {
		for (CorpInfo c : disclosureService.searchByName(stockCode)) {
			if (c.getStockCode().equals(stockCode)) {
				return c;
			}
		}
		return null;
	}
}

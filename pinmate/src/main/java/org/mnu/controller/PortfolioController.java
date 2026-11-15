package org.mnu.controller;

import javax.servlet.http.HttpSession;

import org.mnu.portfolio.PortfolioException;
import org.mnu.portfolio.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 포트폴리오 화면(UC-004)과 테마/섹터 표시(UC-012).
 * 등록·수정·삭제는 POST 로 처리한 뒤 /portfolio 로 redirect 한다. (새로고침해도 다시 전송되지 않게)
 */
@Controller
public class PortfolioController {

	private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);

	@Autowired
	private PortfolioService portfolioService;

	@RequestMapping(value = "/portfolio", method = RequestMethod.GET)
	public String portfolio(@RequestParam(value = "q", required = false) String q, HttpSession session, Model model) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			model.addAttribute("summary", portfolioService.getSummary(userId));
		} catch (DataAccessException e) {
			logger.error("Load portfolio error", e);
			model.addAttribute("error", LoginController.DB_ERROR_MSG);
		}
		if (q != null && !q.trim().isEmpty()) {
			model.addAttribute("q", q.trim());
			model.addAttribute("searchAvailable", portfolioService.isSearchAvailable());
			model.addAttribute("searchResults", portfolioService.search(q.trim()));
		}
		return "portfolio";
	}

	@RequestMapping(value = "/portfolio/add", method = RequestMethod.POST)
	public String add(@RequestParam(value = "stockCode", defaultValue = "") String stockCode,
			@RequestParam(value = "quantity", defaultValue = "") String quantity,
			@RequestParam(value = "avgPrice", defaultValue = "") String avgPrice,
			HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			String name = portfolioService.add(userId, stockCode, quantity, avgPrice);
			redirect.addFlashAttribute("flashMsg", name + " 종목을 포트폴리오에 등록했습니다.");
		} catch (PortfolioException e) {
			redirect.addFlashAttribute("flashError", e.getMessage());
		} catch (DataAccessException e) {
			logger.error("Add holding error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
		}
		return "redirect:/portfolio";
	}

	@RequestMapping(value = "/portfolio/update", method = RequestMethod.POST)
	public String update(@RequestParam("holdingId") long holdingId,
			@RequestParam(value = "quantity", defaultValue = "") String quantity,
			@RequestParam(value = "avgPrice", defaultValue = "") String avgPrice,
			HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			portfolioService.update(userId, holdingId, quantity, avgPrice);
			redirect.addFlashAttribute("flashMsg", "보유 정보를 수정했습니다.");
		} catch (PortfolioException e) {
			redirect.addFlashAttribute("flashError", e.getMessage());
		} catch (DataAccessException e) {
			logger.error("Update holding error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
		}
		return "redirect:/portfolio";
	}

	@RequestMapping(value = "/portfolio/delete", method = RequestMethod.POST)
	public String delete(@RequestParam("holdingId") long holdingId, HttpSession session, RedirectAttributes redirect) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			portfolioService.delete(userId, holdingId);
			redirect.addFlashAttribute("flashMsg", "종목을 삭제했습니다.");
		} catch (PortfolioException e) {
			redirect.addFlashAttribute("flashError", e.getMessage());
		} catch (DataAccessException e) {
			logger.error("Delete holding error", e);
			redirect.addFlashAttribute("flashError", LoginController.DB_ERROR_MSG);
		}
		return "redirect:/portfolio";
	}

}

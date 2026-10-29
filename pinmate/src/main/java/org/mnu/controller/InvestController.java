package org.mnu.controller;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.mnu.dao.InvestDao;
import org.mnu.invest.InvestResult;
import org.mnu.invest.InvestType;
import org.mnu.invest.Survey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 마이페이지(진단 결과 조회)와 재진단.
 */
@Controller
public class InvestController {

	private static final Logger logger = LoggerFactory.getLogger(InvestController.class);

	@Autowired
	private InvestDao investDao;

	@RequestMapping(value = "/mypage", method = RequestMethod.GET)
	public String mypage(HttpSession session, Model model) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		try {
			List<InvestResult> history = investDao.findByUser(userId);
			model.addAttribute("history", history);
			if (!history.isEmpty()) {
				model.addAttribute("latest", history.get(0));
			}
		} catch (DataAccessException e) {
			logger.error("Load invest result error", e);
			model.addAttribute("error", LoginController.DB_ERROR_MSG);
		}
		return "mypage";
	}

	@RequestMapping(value = "/survey", method = RequestMethod.GET)
	public String surveyForm(HttpSession session, Model model) {
		if (session.getAttribute("loginUser") == null) {
			return "redirect:/login";
		}
		model.addAttribute("questions", Survey.getQuestions());
		return "survey";
	}

	@RequestMapping(value = "/survey", method = RequestMethod.POST)
	public String survey(HttpServletRequest request, HttpSession session, Model model) {
		String userId = (String) session.getAttribute("loginUser");
		if (userId == null) {
			return "redirect:/login";
		}
		model.addAttribute("questions", Survey.getQuestions());

		Map<InvestType, Integer> scores = Survey.score(request);
		if (scores == null) {
			model.addAttribute("error", LoginController.SURVEY_INCOMPLETE_MSG);
			return "survey";
		}
		try {
			investDao.insert(userId, Survey.decide(scores), scores);
		} catch (DataAccessException e) {
			logger.error("Save invest result error", e);
			model.addAttribute("error", LoginController.DB_ERROR_MSG);
			return "survey";
		}
		logger.info("Re-diagnosis saved: {}", userId);
		return "redirect:/mypage?diagnosed";
	}

}

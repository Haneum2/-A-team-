package org.mnu.controller;

import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;

import org.mnu.dao.MemberDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Handles login / logout / sign-up requests.
 */
@Controller
public class LoginController {

	private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

	// 비밀번호 규칙: 8자리 이상, 특수문자 1개 이상 포함
	private static final Pattern PW_PATTERN = Pattern.compile("^(?=.*[^A-Za-z0-9\\s]).{8,}$");
	private static final String PW_RULE_MSG = "비밀번호는 8자리 이상, 특수문자를 포함해야 합니다.";
	private static final String DB_ERROR_MSG = "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

	@Autowired
	private MemberDao memberDao;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String loginForm() {
		return "login";
	}

	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public String login(@RequestParam("userId") String userId,
			@RequestParam("password") String password,
			HttpSession session, Model model) {

		if (!PW_PATTERN.matcher(password).matches()) {
			model.addAttribute("error", PW_RULE_MSG);
			return "login";
		}

		try {
			String hash = memberDao.findPasswordHash(userId.trim());
			if (hash != null && passwordEncoder.matches(password, hash)) {
				logger.info("Login success: {}", userId);
				session.setAttribute("loginUser", userId.trim());
				return "redirect:/";
			}
		} catch (DataAccessException e) {
			logger.error("Login DB error", e);
			model.addAttribute("error", DB_ERROR_MSG);
			return "login";
		}

		logger.info("Login failed: {}", userId);
		model.addAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
		return "login";
	}

	@RequestMapping(value = "/register", method = RequestMethod.GET)
	public String registerForm() {
		return "register";
	}

	@RequestMapping(value = "/register", method = RequestMethod.POST)
	public String register(@RequestParam("userId") String userId,
			@RequestParam("password") String password,
			@RequestParam("passwordConfirm") String passwordConfirm,
			Model model) {

		String id = userId.trim();
		String error = null;
		if (id.isEmpty()) {
			error = "아이디를 입력해 주세요.";
		} else if (!PW_PATTERN.matcher(password).matches()) {
			error = PW_RULE_MSG;
		} else if (!password.equals(passwordConfirm)) {
			error = "비밀번호 확인이 일치하지 않습니다.";
		} else {
			try {
				memberDao.insert(id, passwordEncoder.encode(password));
			} catch (DuplicateKeyException e) {
				error = "이미 사용 중인 아이디입니다.";
			} catch (DataAccessException e) {
				logger.error("Sign-up DB error", e);
				error = DB_ERROR_MSG;
			}
		}

		if (error != null) {
			model.addAttribute("error", error);
			return "register";
		}

		logger.info("Sign-up success: {}", id);
		return "redirect:/login?registered";
	}

	@RequestMapping(value = "/logout", method = RequestMethod.GET)
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/";
	}

}

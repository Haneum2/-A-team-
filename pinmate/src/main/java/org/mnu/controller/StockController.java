package org.mnu.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.mnu.disclosure.CorpInfo;
import org.mnu.disclosure.DisclosureItem;
import org.mnu.disclosure.DisclosureService;
import org.mnu.disclosure.DisclosureService.DisclosureResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 종목 검색 및 종목 상세 (SFR-005 / UC-005).
 * 종목 목록과 공시는 DART(DisclosureService)를 사용한다.
 * 시세는 데이터 이용 조건 확인 전이라 아직 연결하지 않는다.
 */
@Controller
public class StockController {

	private static final Logger logger = LoggerFactory.getLogger(StockController.class);
	private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	/** 상세 화면에 보여줄 최근 공시 건수 */
	private static final int DISCLOSURE_PREVIEW = 5;

	@Autowired
	private DisclosureService disclosureService;

	/** UC-005 1~2: 종목명 또는 종목코드로 검색 */
	@RequestMapping(value = "/stock/search", method = RequestMethod.GET)
	public String search(@RequestParam(required = false) String query,
			Model model, HttpSession session) {

		if (session.getAttribute("loginUser") == null) {
			return "redirect:/login";
		}
		if (!disclosureService.isApiKeyConfigured()) {
			model.addAttribute("apiKeyMissing", true);
			return "stock-search";
		}

		if (query != null && !query.trim().isEmpty()) {
			List<CorpInfo> results = disclosureService.searchByName(query.trim());
			model.addAttribute("searchResults", results);
			model.addAttribute("query", query.trim());
			logger.info("종목 검색: query={}, 결과 {}건", query.trim(), results.size());
		}
		return "stock-search";
	}

	/** UC-005 3~5: 종목 상세 (공시 포함) */
	@RequestMapping(value = "/stock/{stockCode}", method = RequestMethod.GET)
	public String detail(@PathVariable String stockCode, Model model, HttpSession session) {

		if (session.getAttribute("loginUser") == null) {
			return "redirect:/login";
		}
		if (!disclosureService.isApiKeyConfigured()) {
			model.addAttribute("apiKeyMissing", true);
			return "stock-detail";
		}

		CorpInfo corp = findByStockCode(stockCode.trim());
		if (corp == null) {
			model.addAttribute("notFound", true);
			model.addAttribute("stockCode", stockCode);
			return "stock-detail";
		}
		model.addAttribute("corp", corp);

		DisclosureResult result = disclosureService.getDisclosures(corp.getStockCode());
		if (result.getErrorMsg() != null) {
			model.addAttribute("dartError", result.getErrorMsg());
		} else {
			List<DisclosureItem> items = result.getItems();
			model.addAttribute("disclosures",
					items.size() > DISCLOSURE_PREVIEW ? items.subList(0, DISCLOSURE_PREVIEW) : items);
			model.addAttribute("disclosureTotal", items.size());
			model.addAttribute("stale", result.isStale());
			model.addAttribute("asOfStr", result.getAsOf() != null ? result.getAsOf().format(DISPLAY_FMT) : "");
		}
		return "stock-detail";
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

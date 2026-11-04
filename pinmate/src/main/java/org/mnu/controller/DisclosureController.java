package org.mnu.controller;

import org.mnu.disclosure.DisclosureItem;
import org.mnu.disclosure.DisclosureService;
import org.mnu.disclosure.DisclosureService.DisclosureResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DisclosureController {

    private static final Logger logger = LoggerFactory.getLogger(DisclosureController.class);
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private DisclosureService disclosureService;

    @RequestMapping(value = "/disclosure", method = RequestMethod.GET)
    public String disclosure(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "false") boolean keyOnly,
            Model model, HttpSession session) {

        if (session.getAttribute("loginUser") == null) {
            return "redirect:/login";
        }

        if (!disclosureService.isApiKeyConfigured()) {
            model.addAttribute("apiKeyMissing", true);
            return "disclosure";
        }

        if (query != null && !query.trim().isEmpty()) {
            model.addAttribute("searchResults", disclosureService.searchByName(query.trim()));
            model.addAttribute("query", query);
        }

        if (stockCode != null && !stockCode.trim().isEmpty()) {
            DisclosureResult result = disclosureService.getDisclosures(stockCode.trim());

            if (result.getErrorMsg() != null) {
                model.addAttribute("dartError", result.getErrorMsg());
            } else {
                List<DisclosureItem> items = result.getItems();
                if (keyOnly) {
                    items = items.stream().filter(DisclosureItem::isKey).collect(Collectors.toList());
                }
                model.addAttribute("disclosures", items);
                model.addAttribute("stale", result.isStale());

                LocalDateTime asOf = result.getAsOf();
                model.addAttribute("asOfStr", asOf != null ? asOf.format(DISPLAY_FMT) : "");
            }

            model.addAttribute("stockCode", stockCode.trim());
            model.addAttribute("keyOnly", keyOnly);

            logger.info("공시 조회: stockCode={}, 결과 {}건",
                    stockCode, result.getErrorMsg() != null ? 0 : result.getItems().size());
        }

        return "disclosure";
    }
}

package org.mnu.controller;

import org.mnu.dao.PortfolioDao;
import org.mnu.dao.WatchlistDao;
import org.mnu.news.NewsService;
import org.mnu.news.NewsService.NewsResult;
import org.mnu.portfolio.Holding;
import org.mnu.watchlist.WatchItem;
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
import java.util.ArrayList;
import java.util.List;

@Controller
public class NewsController {

    private static final Logger           logger      = LoggerFactory.getLogger(NewsController.class);
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired private NewsService  newsService;
    @Autowired private PortfolioDao portfolioDao;
    @Autowired private WatchlistDao watchlistDao;

    @RequestMapping(value = "/news", method = RequestMethod.GET)
    public String news(
            @RequestParam(value = "tab", required = false, defaultValue = "all") String tab,
            Model model, HttpSession session) {

        String userId = (String) session.getAttribute("loginUser");
        if (userId == null) return "redirect:/login";

        // tab 파라미터 무결성 검증
        if (!"all".equals(tab) && !"holding".equals(tab) && !"watch".equals(tab)) {
            tab = "all";
        }
        model.addAttribute("activeTab", tab);

        NewsResult result;

        if ("holding".equals(tab)) {
            List<Holding> holdings = portfolioDao.findByUser(userId);
            List<String>  names    = new ArrayList<String>();
            for (Holding h : holdings) names.add(h.getStockName());
            model.addAttribute("hasStocks", !holdings.isEmpty());
            result = newsService.getHoldingNews(names);

        } else if ("watch".equals(tab)) {
            List<WatchItem> watches = watchlistDao.findByUser(userId);
            List<String>    names   = new ArrayList<String>();
            for (WatchItem w : watches) names.add(w.getStockName());
            model.addAttribute("hasStocks", !watches.isEmpty());
            result = newsService.getWatchNews(names);

        } else {
            model.addAttribute("hasStocks", true);
            result = newsService.getAllNews();
        }

        model.addAttribute("news",  result.getItems());
        model.addAttribute("stale", result.isStale());

        LocalDateTime asOf = result.getAsOf();
        model.addAttribute("asOfStr", asOf != null ? asOf.format(DISPLAY_FMT) : "");

        logger.info("뉴스 조회: userId={}, tab={}, 결과 {}건", userId, tab, result.getItems().size());

        return "news";
    }
}

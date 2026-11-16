package org.mnu.controller;

import org.mnu.dao.PortfolioDao;
import org.mnu.dao.WatchlistDao;
import org.mnu.news.NewsItem;
import org.mnu.news.NewsService;
import org.mnu.news.NewsService.NewsResult;
import org.mnu.news.sentiment.NewsBriefing;
import org.mnu.news.sentiment.NewsBriefingService;
import org.mnu.news.sentiment.SentimentAnalyzer;
import org.mnu.news.sentiment.SentimentTag;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class NewsController {

    private static final Logger            logger      = LoggerFactory.getLogger(NewsController.class);
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired private NewsService         newsService;
    @Autowired private PortfolioDao        portfolioDao;
    @Autowired private WatchlistDao        watchlistDao;
    @Autowired private SentimentAnalyzer   sentimentAnalyzer;
    @Autowired private NewsBriefingService briefingService;

    @RequestMapping(value = "/news", method = RequestMethod.GET)
    public String news(
            @RequestParam(value = "tab",    required = false, defaultValue = "all") String tab,
            @RequestParam(value = "filter", required = false, defaultValue = "all") String filter,
            Model model, HttpSession session) {

        String userId = (String) session.getAttribute("loginUser");
        if (userId == null) return "redirect:/login";

        // 파라미터 무결성 검증
        if (!"all".equals(tab) && !"holding".equals(tab) && !"watch".equals(tab)) {
            tab = "all";
        }
        if (!"all".equals(filter) && !"positive".equals(filter) && !"negative".equals(filter)) {
            filter = "all";
        }

        model.addAttribute("activeTab",    tab);
        model.addAttribute("activeFilter", filter);

        NewsResult result;
        List<String> stockNames = new ArrayList<String>();

        if ("holding".equals(tab)) {
            List<Holding> holdings = portfolioDao.findByUser(userId);
            for (Holding h : holdings) stockNames.add(h.getStockName());
            model.addAttribute("hasStocks", !holdings.isEmpty());
            result = newsService.getHoldingNews(stockNames);

        } else if ("watch".equals(tab)) {
            List<WatchItem> watches = watchlistDao.findByUser(userId);
            for (WatchItem w : watches) stockNames.add(w.getStockName());
            model.addAttribute("hasStocks", !watches.isEmpty());
            result = newsService.getWatchNews(stockNames);

        } else {
            model.addAttribute("hasStocks", true);
            result = newsService.getAllNews();
        }

        // 감성 분석 적용
        List<NewsItem> allItems = result.getItems();
        for (NewsItem item : allItems) {
            item.setSentiment(sentimentAnalyzer.analyze(item.getTitle()));
        }

        // 감성 필터 적용
        List<NewsItem> filtered;
        if ("positive".equals(filter)) {
            filtered = new ArrayList<NewsItem>();
            for (NewsItem item : allItems) {
                if (SentimentTag.POSITIVE == item.getSentiment().getTag()) filtered.add(item);
            }
        } else if ("negative".equals(filter)) {
            filtered = new ArrayList<NewsItem>();
            for (NewsItem item : allItems) {
                if (SentimentTag.NEGATIVE == item.getSentiment().getTag()) filtered.add(item);
            }
        } else {
            filtered = allItems;
        }

        model.addAttribute("news",  filtered);
        model.addAttribute("stale", result.isStale());

        LocalDateTime asOf = result.getAsOf();
        model.addAttribute("asOfStr", asOf != null ? asOf.format(DISPLAY_FMT) : "");

        // 종목 탭이고 종목이 있을 때만 브리핑 생성
        if (!"all".equals(tab) && !stockNames.isEmpty()) {
            // relatedStocks 기준으로 종목별 뉴스 그룹화 (allItems 기준 — 필터 전)
            Map<String, List<NewsItem>> stockNewsMap =
                    new LinkedHashMap<String, List<NewsItem>>();
            for (String sn : stockNames) {
                stockNewsMap.put(sn, new ArrayList<NewsItem>());
            }
            for (NewsItem item : allItems) {
                for (String sn : item.getRelatedStocks()) {
                    if (stockNewsMap.containsKey(sn)) {
                        stockNewsMap.get(sn).add(item);
                    }
                }
            }
            List<NewsBriefing> briefings = briefingService.generate(stockNewsMap);
            model.addAttribute("briefings", briefings);
        }

        logger.info("뉴스 조회: userId={}, tab={}, filter={}, 전체{}건/표시{}건",
                new Object[]{userId, tab, filter, allItems.size(), filtered.size()});

        return "news";
    }
}

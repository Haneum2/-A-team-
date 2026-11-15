package org.mnu.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NewsService {

    private static final Logger logger = LoggerFactory.getLogger(NewsService.class);

    private static final long   CACHE_TTL_MIN   = 10L;
    private static final int    CONNECT_TIMEOUT = 3_000;
    private static final int    READ_TIMEOUT    = 5_000;
    private static final int    MAX_ITEMS       = 50;
    private static final int    MAX_STOCKS      = 10;
    private static final String GOOGLE_RSS =
            "https://news.google.com/rss/search?q=%s&hl=ko&gl=KR&ceid=KR:ko";
    private static final String USER_AGENT = "Mozilla/5.0 pinmate-news/1.0";

    // RFC 822 패턴 배열 (SimpleDateFormat은 메서드 내 지역 변수로 사용해 thread-safety 보장)
    private static final String[] RFC822_PATTERNS = {
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z"
    };

    private static final List<String> ECONOMY_RSS_URLS;
    // 피드 URL → 표시할 언론사명 (채널 제목 대신 사용)
    private static final Map<String, String> FEED_PUBLISHERS;
    static {
        List<String>        urls = new ArrayList<String>();
        Map<String, String> pub  = new LinkedHashMap<String, String>();

        urls.add("https://www.hankyung.com/feed/finance");   pub.put("https://www.hankyung.com/feed/finance",  "한국경제");
        urls.add("https://www.hankyung.com/feed/economy");   pub.put("https://www.hankyung.com/feed/economy",  "한국경제");
        urls.add("https://www.sedaily.com/rss/economy");     pub.put("https://www.sedaily.com/rss/economy",    "서울경제");
        urls.add("https://www.yna.co.kr/rss/economy.xml");   pub.put("https://www.yna.co.kr/rss/economy.xml",  "연합뉴스");

        ECONOMY_RSS_URLS = Collections.unmodifiableList(urls);
        FEED_PUBLISHERS  = Collections.unmodifiableMap(pub);
    }

    private final Map<String, CachedFeed> feedCache = new ConcurrentHashMap<String, CachedFeed>();

    // -------------------------------------------------------------------------
    // 내부 캐시 클래스
    // -------------------------------------------------------------------------

    private static class CachedFeed {
        final List<NewsItem> items;
        final LocalDateTime  fetchTime;
        final boolean        fetchFailed;

        CachedFeed(List<NewsItem> items, LocalDateTime fetchTime, boolean fetchFailed) {
            this.items       = items;
            this.fetchTime   = fetchTime;
            this.fetchFailed = fetchFailed;
        }

        boolean isExpired() {
            return ChronoUnit.MINUTES.between(fetchTime, LocalDateTime.now()) >= CACHE_TTL_MIN;
        }
    }

    // -------------------------------------------------------------------------
    // 결과 클래스
    // -------------------------------------------------------------------------

    public static class NewsResult {
        private final List<NewsItem> items;
        private final LocalDateTime  asOf;
        private final boolean        stale;
        private final boolean        noStocks;

        public NewsResult(List<NewsItem> items, LocalDateTime asOf,
                          boolean stale, boolean noStocks) {
            this.items    = items;
            this.asOf     = asOf;
            this.stale    = stale;
            this.noStocks = noStocks;
        }

        public List<NewsItem> getItems()   { return items; }
        public LocalDateTime  getAsOf()    { return asOf; }
        public boolean        isStale()    { return stale; }
        public boolean        isNoStocks() { return noStocks; }
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    public NewsResult getAllNews() {
        List<NewsItem> all   = new ArrayList<NewsItem>();
        boolean        stale = false;
        LocalDateTime  asOf  = null;

        for (String url : ECONOMY_RSS_URLS) {
            String     cacheKey = "rss:" + url;
            CachedFeed cf       = fetchFeed(url, cacheKey, false);
            if (cf.fetchFailed) stale = true;
            all.addAll(cf.items);
            if (asOf == null || cf.fetchTime.isBefore(asOf)) {
                asOf = cf.fetchTime;
            }
        }
        return new NewsResult(deduplicateAndSort(all), asOf, stale, false);
    }

    public NewsResult getHoldingNews(List<String> stockNames) {
        return getStockNews(stockNames);
    }

    public NewsResult getWatchNews(List<String> stockNames) {
        return getStockNews(stockNames);
    }

    // -------------------------------------------------------------------------
    // private — 종목 뉴스 수집
    // -------------------------------------------------------------------------

    private NewsResult getStockNews(List<String> stockNames) {
        if (stockNames == null || stockNames.isEmpty()) {
            return new NewsResult(Collections.<NewsItem>emptyList(), null, false, true);
        }

        List<String> names = stockNames.size() > MAX_STOCKS
                ? stockNames.subList(0, MAX_STOCKS)
                : stockNames;

        List<NewsItem> all   = new ArrayList<NewsItem>();
        boolean        stale = false;
        LocalDateTime  asOf  = null;

        for (String name : names) {
            String cacheKey = "stock:" + name;
            String rssUrl;
            try {
                rssUrl = String.format(GOOGLE_RSS, URLEncoder.encode(name, "UTF-8"));
            } catch (Exception e) {
                logger.warn("종목명 인코딩 실패: {}", name);
                continue;
            }

            CachedFeed cf = fetchFeed(rssUrl, cacheKey, true);
            if (cf.fetchFailed) stale = true;
            // relatedStocks 배지 주입
            for (NewsItem item : cf.items) {
                if (!item.getRelatedStocks().contains(name)) {
                    item.getRelatedStocks().add(name);
                }
            }
            all.addAll(cf.items);
            if (asOf == null || cf.fetchTime.isBefore(asOf)) {
                asOf = cf.fetchTime;
            }
        }
        return new NewsResult(deduplicateAndSort(all), asOf, stale, false);
    }

    // -------------------------------------------------------------------------
    // private — 단일 피드 캐시 적용 fetch
    // -------------------------------------------------------------------------

    private CachedFeed fetchFeed(String rssUrl, String cacheKey, boolean isGoogle) {
        CachedFeed cached = feedCache.get(cacheKey);
        if (cached != null && !cached.isExpired() && !cached.fetchFailed) {
            return cached;
        }

        try {
            InputStream        is        = openStream(rssUrl);
            String             publisher = FEED_PUBLISHERS.get(rssUrl); // null이면 채널 제목 사용
            List<NewsItem>     items     = parseRss(is, isGoogle, publisher);
            CachedFeed         fresh     = new CachedFeed(items, LocalDateTime.now(), false);
            feedCache.put(cacheKey, fresh);
            return fresh;
        } catch (Exception e) {
            logger.warn("RSS 피드 수집 실패 [{}]: {}", rssUrl, e.getMessage());
            if (cached != null) {
                // stale 캐시로 교체해 만료 재시도를 막음
                CachedFeed staleCache = new CachedFeed(cached.items, LocalDateTime.now(), true);
                feedCache.put(cacheKey, staleCache);
                return staleCache;
            }
            CachedFeed empty = new CachedFeed(
                    Collections.<NewsItem>emptyList(), LocalDateTime.now(), true);
            feedCache.put(cacheKey, empty);
            return empty;
        }
    }

    // -------------------------------------------------------------------------
    // private — HTTP
    // -------------------------------------------------------------------------

    private InputStream openStream(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new Exception("HTTP " + code);
        }
        return conn.getInputStream();
    }

    // -------------------------------------------------------------------------
    // private — DOM 파싱 (SER-002 XXE 방지)
    // -------------------------------------------------------------------------

    private List<NewsItem> parseRss(InputStream is, boolean isGoogle,
                                    String publisherOverride) throws Exception {
        DocumentBuilderFactory factory = createSecureFactory();
        DocumentBuilder        builder = factory.newDocumentBuilder();
        Document               doc     = builder.parse(is);

        // 채널 제목은 publisherOverride 가 없을 때의 fallback
        String channelTitle = "";
        NodeList channels = doc.getElementsByTagName("channel");
        if (channels.getLength() > 0) {
            NodeList titleNodes = ((Element) channels.item(0)).getElementsByTagName("title");
            if (titleNodes.getLength() > 0) {
                channelTitle = titleNodes.item(0).getTextContent().trim();
            }
        }
        // 국내 피드: publisherOverride > 채널 제목 순으로 사용
        String feedSource = (publisherOverride != null && !publisherOverride.isEmpty())
                ? publisherOverride : channelTitle;

        NodeList       itemNodes = doc.getElementsByTagName("item");
        List<NewsItem> result    = new ArrayList<NewsItem>();

        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element item      = (Element) itemNodes.item(i);
            String  rawTitle  = getChildText(item, "title");
            String  link      = getChildText(item, "link");
            String  pubDateStr = getChildText(item, "pubDate");
            String  sourceTag = getChildText(item, "source");

            if (link == null || link.trim().isEmpty()) continue;

            String title  = isGoogle ? cleanTitle(rawTitle)               : rawTitle;
            String source = isGoogle ? extractSource(rawTitle, sourceTag) : feedSource;

            NewsItem ni = new NewsItem();
            ni.setTitle(title  != null ? title.trim()  : "");
            ni.setSource(source != null ? source.trim() : "");
            ni.setLink(link.trim());
            ni.setPubDate(parseRfc822(pubDateStr));
            result.add(ni);
        }
        return result;
    }

    // XXE 방지 — factory는 thread-safe하지 않으므로 호출마다 새로 생성
    private DocumentBuilderFactory createSecureFactory() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",           true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",          false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",        false);
        f.setExpandEntityReferences(false);
        return f;
    }

    // -------------------------------------------------------------------------
    // private — 파싱 유틸
    // -------------------------------------------------------------------------

    private String getChildText(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() == 0) return "";
        return nl.item(0).getTextContent();
    }

    private Date parseRfc822(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        for (String pattern : RFC822_PATTERNS) {
            try {
                // SimpleDateFormat은 not thread-safe → 지역 변수로 생성
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
                return sdf.parse(dateStr.trim());
            } catch (ParseException ignored) {
                // 다음 패턴 시도
            }
        }
        logger.warn("RFC 822 날짜 파싱 실패: {}", dateStr);
        return null;
    }

    private String cleanTitle(String rawTitle) {
        if (rawTitle == null) return "";
        int idx = rawTitle.lastIndexOf(" - ");
        return (idx > 0) ? rawTitle.substring(0, idx).trim() : rawTitle.trim();
    }

    private String extractSource(String rawTitle, String sourceTag) {
        if (sourceTag != null && !sourceTag.trim().isEmpty()) {
            return sourceTag.trim();
        }
        if (rawTitle == null) return "";
        int idx = rawTitle.lastIndexOf(" - ");
        if (idx > 0 && idx < rawTitle.length() - 3) {
            return rawTitle.substring(idx + 3).trim();
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // private — 중복제거 + 정렬 + 50건
    // -------------------------------------------------------------------------

    private List<NewsItem> deduplicateAndSort(List<NewsItem> all) {
        // 링크 기준 중복 제거, 같은 링크는 relatedStocks 배지 병합
        Map<String, NewsItem> linkMap = new LinkedHashMap<String, NewsItem>();
        for (NewsItem item : all) {
            String link = item.getLink();
            if (link == null || link.isEmpty()) continue;
            if (linkMap.containsKey(link)) {
                for (String stock : item.getRelatedStocks()) {
                    if (!linkMap.get(link).getRelatedStocks().contains(stock)) {
                        linkMap.get(link).getRelatedStocks().add(stock);
                    }
                }
            } else {
                linkMap.put(link, item);
            }
        }

        List<NewsItem> deduped = new ArrayList<NewsItem>(linkMap.values());
        Collections.sort(deduped, new Comparator<NewsItem>() {
            @Override
            public int compare(NewsItem a, NewsItem b) {
                if (a.getPubDate() == null && b.getPubDate() == null) return 0;
                if (a.getPubDate() == null) return 1;
                if (b.getPubDate() == null) return -1;
                return b.getPubDate().compareTo(a.getPubDate());
            }
        });

        return deduped.size() > MAX_ITEMS ? deduped.subList(0, MAX_ITEMS) : deduped;
    }
}

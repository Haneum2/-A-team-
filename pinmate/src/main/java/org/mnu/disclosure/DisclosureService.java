package org.mnu.disclosure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.annotation.PostConstruct;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DisclosureService {

    private static final Logger logger = LoggerFactory.getLogger(DisclosureService.class);

    private static final String CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=";
    private static final String LIST_URL      = "https://opendart.fss.or.kr/api/list.json";
    private static final long   CACHE_TTL_MIN = 10L;
    private static final int    HTTP_TIMEOUT  = 10_000;

    @Value("${dart.api.key:}")
    private String apiKey;

    // stock_code -> corp_code (상장사만)
    private volatile Map<String, String>   stockToCorpCode = new ConcurrentHashMap<>();
    // corp_code -> CorpInfo
    private volatile Map<String, CorpInfo> corpCodeToInfo  = new ConcurrentHashMap<>();

    // 공시 캐시: stockCode -> CachedResult
    private final Map<String, CachedResult> disclosureCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (!isApiKeyConfigured()) {
            logger.warn("DART API 키가 설정되지 않았습니다. api.properties에 dart.api.key를 입력하세요.");
            return;
        }
        loadCorpCodes();
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dart-corpcode-refresh");
            t.setDaemon(true);
            return t;
        });
        sched.scheduleAtFixedDelay(this::loadCorpCodes, 24, 24, TimeUnit.HOURS);
    }

    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    // ---- corpCode 관리 ----

    private void loadCorpCodes() {
        logger.info("DART corpCode.xml 로딩 시작");
        try {
            URL url = new URL(CORP_CODE_URL + apiKey.trim());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HTTP_TIMEOUT);
            conn.setReadTimeout(30_000); // zip은 크므로 30s
            conn.connect();

            if (conn.getResponseCode() != 200) {
                logger.error("corpCode.xml 응답 코드: {}", conn.getResponseCode());
                return;
            }

            Map<String, String>   newStockMap = new ConcurrentHashMap<>();
            Map<String, CorpInfo> newInfoMap  = new ConcurrentHashMap<>();

            try (ZipInputStream zis = new ZipInputStream(conn.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith("CORPCODE.xml") || entry.getName().endsWith("corpCode.xml")) {
                        parseCorpCodeXml(zis, newStockMap, newInfoMap);
                        break;
                    }
                }
            }

            if (!newStockMap.isEmpty()) {
                stockToCorpCode = newStockMap;
                corpCodeToInfo  = newInfoMap;
                logger.info("DART corpCode 로딩 완료: 상장사 {}개", newStockMap.size());
            } else {
                logger.warn("corpCode.xml에서 상장사 데이터를 찾지 못했습니다.");
            }
        } catch (Exception e) {
            logger.error("DART corpCode.xml 로딩 실패 — 기존 Map 유지", e);
        }
    }

    private void parseCorpCodeXml(InputStream is,
                                   Map<String, String> stockMap,
                                   Map<String, CorpInfo> infoMap) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(is, new DefaultHandler() {
            private String corpCode, corpName, stockCode;
            private StringBuilder buf = new StringBuilder();

            @Override public void startElement(String u, String l, String qName, Attributes a) {
                buf.setLength(0);
            }
            @Override public void characters(char[] ch, int s, int len) {
                buf.append(ch, s, len);
            }
            @Override public void endElement(String u, String l, String qName) {
                String val = buf.toString().trim();
                switch (qName) {
                    case "corp_code":  corpCode  = val; break;
                    case "corp_name":  corpName  = val; break;
                    case "stock_code": stockCode = val; break;
                    case "list":
                        if (corpCode != null && corpName != null
                                && stockCode != null && !stockCode.isEmpty()) {
                            stockMap.put(stockCode, corpCode);
                            infoMap.put(corpCode, new CorpInfo(corpCode, corpName, stockCode));
                        }
                        corpCode = corpName = stockCode = null;
                        break;
                }
            }
        });
    }

    // 종목명 부분일치 검색 (최대 20개)
    public List<CorpInfo> searchByName(String keyword) {
        String lower = keyword.toLowerCase();
        return corpCodeToInfo.values().stream()
                .filter(c -> c.getCorpName().toLowerCase().contains(lower))
                .limit(20)
                .collect(Collectors.toList());
    }

    // ---- 공시 조회 ----

    public DisclosureResult getDisclosures(String stockCode) {
        CachedResult cached = disclosureCache.get(stockCode);

        if (cached != null && !cached.isExpired() && !cached.fetchFailed) {
            return new DisclosureResult(cached.items, cached.fetchTime, false);
        }

        String corpCode = stockToCorpCode.get(stockCode);
        if (corpCode == null) {
            logger.warn("종목코드 {}에 해당하는 DART corpCode 없음", stockCode);
            if (cached != null) {
                return new DisclosureResult(cached.items, cached.fetchTime, true);
            }
            return new DisclosureResult(Collections.emptyList(), null, true);
        }

        try {
            List<DisclosureItem> items = fetchFromDart(corpCode, stockCode);
            LocalDateTime now = LocalDateTime.now();
            disclosureCache.put(stockCode, new CachedResult(items, now, false));
            return new DisclosureResult(items, now, false);
        } catch (Exception e) {
            logger.error("DART 공시 조회 실패 (stockCode={}): {}", stockCode, e.getMessage());
            if (cached != null) {
                return new DisclosureResult(cached.items, cached.fetchTime, true);
            }
            return new DisclosureResult(Collections.emptyList(), null, true);
        }
    }

    // 추후 포트폴리오/관심종목 연결용
    public List<DisclosureItem> getDisclosuresFor(List<String> stockCodes) {
        List<DisclosureItem> all = new ArrayList<>();
        for (String sc : stockCodes) {
            all.addAll(getDisclosures(sc).getItems());
        }
        all.sort(Comparator.comparing(DisclosureItem::getReceiptDate).reversed());
        return all;
    }

    // ---- 내부 헬퍼 ----

    private List<DisclosureItem> fetchFromDart(String corpCode, String stockCode) throws Exception {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        String today   = LocalDate.now().format(fmt);
        String threeMonthsAgo = LocalDate.now().minusMonths(3).format(fmt);

        String urlStr = LIST_URL
                + "?crtfc_key=" + apiKey.trim()
                + "&corp_code=" + corpCode
                + "&bgn_de=" + threeMonthsAgo
                + "&end_de=" + today
                + "&page_count=100";

        String json = httpGet(urlStr);
        JsonNode root = objectMapper.readTree(json);

        String status = root.path("status").asText();
        if (!"000".equals(status)) {
            String msg = root.path("message").asText();
            // 조회 결과 없음(013)은 에러가 아님
            if ("013".equals(status)) {
                return Collections.emptyList();
            }
            throw new RuntimeException("DART API 오류 [" + status + "]: " + msg);
        }

        List<DisclosureItem> result = new ArrayList<>();
        JsonNode list = root.path("list");
        if (list.isArray()) {
            for (JsonNode node : list) {
                String typeCd = node.path("report_tp").asText();
                if (!"A".equals(typeCd) && !"B".equals(typeCd) && !"I".equals(typeCd)) {
                    continue;
                }
                DisclosureItem item = new DisclosureItem();
                item.setRcpNo(node.path("rcp_no").asText());
                item.setCorpName(node.path("corp_name").asText());
                item.setStockCode(stockCode);
                item.setReportTypeCd(typeCd);
                item.setTitle(node.path("report_nm").asText());
                item.setReceiptDate(node.path("rcept_dt").asText());
                item.setKey(isKeyDisclosure(typeCd, item.getTitle()));
                result.add(item);
            }
        }
        return result;
    }

    private boolean isKeyDisclosure(String typeCd, String title) {
        if ("A".equals(typeCd)) {
            return title.contains("사업보고서") || title.contains("반기보고서") || title.contains("분기보고서");
        }
        if ("B".equals(typeCd) || "I".equals(typeCd)) {
            return title.contains("유상증자") || title.contains("무상증자")
                    || title.contains("잠정실적") || title.contains("영업(잠정)");
        }
        return false;
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(HTTP_TIMEOUT);
        conn.setReadTimeout(HTTP_TIMEOUT);
        conn.connect();

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("HTTP " + code + " from " + urlStr);
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // ---- 내부 클래스 ----

    private static class CachedResult {
        final List<DisclosureItem> items;
        final LocalDateTime fetchTime;
        final boolean fetchFailed;

        CachedResult(List<DisclosureItem> items, LocalDateTime fetchTime, boolean fetchFailed) {
            this.items       = items;
            this.fetchTime   = fetchTime;
            this.fetchFailed = fetchFailed;
        }

        boolean isExpired() {
            return ChronoUnit.MINUTES.between(fetchTime, LocalDateTime.now()) >= CACHE_TTL_MIN;
        }
    }

    public static class DisclosureResult {
        private final List<DisclosureItem> items;
        private final LocalDateTime asOf;
        private final boolean stale;

        public DisclosureResult(List<DisclosureItem> items, LocalDateTime asOf, boolean stale) {
            this.items = items != null ? items : Collections.emptyList();
            this.asOf  = asOf;
            this.stale = stale;
        }

        public List<DisclosureItem> getItems()  { return items; }
        public LocalDateTime        getAsOf()   { return asOf;  }
        public boolean              isStale()   { return stale; }
    }
}

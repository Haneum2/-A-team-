package org.mnu.news.sentiment;

import org.mnu.news.NewsItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 종목별 뉴스 3줄 브리핑 생성기.
 * 이미 sentiment가 설정된 NewsItem 목록을 받아 종목별로 집계한다.
 * (기사 본문 미사용 — 제목만으로 집계, 저작권 준수)
 */
@Component
public class NewsBriefingService {

    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                "주식", "증시", "코스피", "코스닥", "투자", "시장", "주가", "뉴스",
                "오늘", "한국", "국내", "관련", "전망", "분기", "상반기", "하반기",
                "올해", "내년", "지난해", "이번", "다음", "기업", "회사", "대표"
            )));

    /**
     * 종목명 → 뉴스 목록 맵을 받아 종목별 브리핑 리스트를 반환한다.
     * 기사가 없는 종목은 결과에 포함하지 않는다.
     *
     * @param stockNewsMap 종목명을 키로, 해당 종목 관련 NewsItem 리스트를 값으로 하는 맵
     */
    public List<NewsBriefing> generate(Map<String, List<NewsItem>> stockNewsMap) {
        List<NewsBriefing> briefings = new ArrayList<NewsBriefing>();

        for (Map.Entry<String, List<NewsItem>> entry : stockNewsMap.entrySet()) {
            String          stockName = entry.getKey();
            List<NewsItem>  items     = entry.getValue();
            if (items.isEmpty()) continue;

            NewsBriefing briefing = new NewsBriefing();
            briefing.setStockName(stockName);

            int pos = 0, neg = 0, neu = 0;
            int maxAbsScore = -1;
            NewsItem headline = null;

            // 제목 토큰 빈도 집계 (삽입 순서 유지 → LinkedHashMap)
            Map<String, Integer> kwFreq = new LinkedHashMap<String, Integer>();

            for (NewsItem item : items) {
                SentimentResult sr = item.getSentiment();
                if (sr == null) continue; // 분석 안 된 항목은 집계 제외

                switch (sr.getTag()) {
                    case POSITIVE: pos++; break;
                    case NEGATIVE: neg++; break;
                    default:       neu++; break;
                }

                // 대표 기사: 감성 점수 절대값이 가장 큰 기사
                if (Math.abs(sr.getScore()) > maxAbsScore) {
                    maxAbsScore = Math.abs(sr.getScore());
                    headline    = item;
                }

                // 제목 토큰화 → 키워드 빈도 누적
                for (String token : tokenize(item.getTitle(), stockName)) {
                    Integer count = kwFreq.get(token);
                    kwFreq.put(token, count == null ? 1 : count + 1);
                }
            }

            briefing.setPositiveCount(pos);
            briefing.setNegativeCount(neg);
            briefing.setNeutralCount(neu);

            if      (pos > neg) briefing.setOverallMood("대체로 긍정적");
            else if (neg > pos) briefing.setOverallMood("대체로 부정적");
            else                briefing.setOverallMood("혼조");

            // 빈도 내림차순 정렬 → 상위 3개
            List<Map.Entry<String, Integer>> sorted =
                    new ArrayList<Map.Entry<String, Integer>>(kwFreq.entrySet());
            Collections.sort(sorted, new Comparator<Map.Entry<String, Integer>>() {
                @Override
                public int compare(Map.Entry<String, Integer> a,
                                   Map.Entry<String, Integer> b) {
                    return b.getValue() - a.getValue();
                }
            });
            List<String> topKws = new ArrayList<String>();
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                topKws.add(sorted.get(i).getKey());
            }
            briefing.setTopKeywords(topKws);

            if (headline != null) {
                briefing.setHeadlineTitle(headline.getTitle());
                briefing.setHeadlineLink(headline.getLink());
            }

            briefings.add(briefing);
        }

        return briefings;
    }

    /** 제목을 토큰화하여 불용어·종목명을 제외한 2자 이상 단어 목록을 반환한다. */
    private List<String> tokenize(String title, String stockName) {
        List<String> tokens = new ArrayList<String>();
        if (title == null || title.isEmpty()) return tokens;

        // 구분자: 공백, 쉼표, 괄호, 따옴표, 꺾쇠, 말줄임표, 하이픈, 슬래시 등
        String[] parts = title.split("[\\s,·()\\[\\]\"'《》「」<>\\-_/\\\\·•·…]+");
        for (String part : parts) {
            String t = part.trim();
            if (t.length() < 2) continue;
            if (STOP_WORDS.contains(t)) continue;
            if (stockName != null && t.equals(stockName)) continue;
            tokens.add(t);
        }
        return tokens;
    }
}

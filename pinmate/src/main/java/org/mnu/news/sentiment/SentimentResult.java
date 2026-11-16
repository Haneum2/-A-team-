package org.mnu.news.sentiment;

import java.util.Collections;
import java.util.List;

public class SentimentResult {

    private final SentimentTag  tag;
    private final int           score;
    private final List<String>  matchedKeywords; // 사전 원문 키워드 — 화면 툴팁용

    public SentimentResult(SentimentTag tag, int score, List<String> matchedKeywords) {
        this.tag             = tag;
        this.score           = score;
        this.matchedKeywords = Collections.unmodifiableList(matchedKeywords);
    }

    public SentimentTag  getTag()             { return tag; }
    public int           getScore()           { return score; }
    public List<String>  getMatchedKeywords() { return matchedKeywords; }

    /** JSP title 속성용: 매칭된 키워드를 ", "로 연결한 문자열 */
    public String getMatchedKeywordsStr() {
        if (matchedKeywords.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matchedKeywords.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(matchedKeywords.get(i));
        }
        return sb.toString();
    }
}

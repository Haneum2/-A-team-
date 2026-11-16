package org.mnu.news.sentiment;

import java.util.Collections;
import java.util.List;

/** 종목별 뉴스 3줄 브리핑 — 기사 본문 없이 제목만으로 집계한 참고 정보 */
public class NewsBriefing {

    private String       stockName;
    private int          positiveCount;
    private int          negativeCount;
    private int          neutralCount;
    private String       overallMood;    // "대체로 긍정적" / "대체로 부정적" / "혼조"
    private List<String> topKeywords;    // 빈도 상위 3개 단어
    private String       headlineTitle;  // 감성 점수 절대값 최대 기사 제목
    private String       headlineLink;   // 대표 기사 원문 링크

    public String       getStockName()     { return stockName; }
    public void         setStockName(String v)      { this.stockName = v; }

    public int          getPositiveCount() { return positiveCount; }
    public void         setPositiveCount(int v)     { this.positiveCount = v; }

    public int          getNegativeCount() { return negativeCount; }
    public void         setNegativeCount(int v)     { this.negativeCount = v; }

    public int          getNeutralCount()  { return neutralCount; }
    public void         setNeutralCount(int v)      { this.neutralCount = v; }

    public String       getOverallMood()   { return overallMood; }
    public void         setOverallMood(String v)    { this.overallMood = v; }

    public List<String> getTopKeywords()   { return topKeywords; }
    public void         setTopKeywords(List<String> v) {
        this.topKeywords = v == null ? Collections.<String>emptyList()
                                     : Collections.unmodifiableList(v);
    }

    public String       getHeadlineTitle() { return headlineTitle; }
    public void         setHeadlineTitle(String v)  { this.headlineTitle = v; }

    public String       getHeadlineLink()  { return headlineLink; }
    public void         setHeadlineLink(String v)   { this.headlineLink = v; }
}

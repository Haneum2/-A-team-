package org.mnu.news.sentiment;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * SentimentAnalyzer 단위 테스트 (Spring Context 불필요).
 * sentiment-dict.txt는 src/main/resources 에 있으며 테스트 클래스패스에서 그대로 접근된다.
 */
public class SentimentAnalyzerTest {

    private SentimentAnalyzer analyzer;

    @Before
    public void setUp() {
        analyzer = new SentimentAnalyzer();
        analyzer.init(); // @PostConstruct 수동 호출 — dict 로드
    }

    // ── 기본 호재 ──────────────────────────────────────────────

    @Test
    public void 사상최대실적_호재() {
        SentimentResult r = analyzer.analyze("삼성전자, 3분기 사상 최대 실적");
        assertEquals("사상최대실적 → POSITIVE", SentimentTag.POSITIVE, r.getTag());
    }

    @Test
    public void 흑자전환_호재() {
        SentimentResult r = analyzer.analyze("OO전자 흑자 전환 성공");
        assertEquals("흑자 전환 → POSITIVE", SentimentTag.POSITIVE, r.getTag());
    }

    // ── 기본 악재 ──────────────────────────────────────────────

    @Test
    public void 횡령_악재() {
        SentimentResult r = analyzer.analyze("OO기업 대표 횡령 혐의");
        assertEquals("횡령 혐의 → NEGATIVE", SentimentTag.NEGATIVE, r.getTag());
    }

    @Test
    public void 적자전환_악재() {
        SentimentResult r = analyzer.analyze("XX건설 3분기 적자 전환");
        assertEquals("적자 전환 → NEGATIVE", SentimentTag.NEGATIVE, r.getTag());
    }

    // ── 맥락 규칙 (longest-match) ──────────────────────────────

    @Test
    public void 적자폭축소_공백포함_호재() {
        // "적자 폭 축소" → 공백 제거 후 "적자폭축소" 매칭 → 호재
        SentimentResult r = analyzer.analyze("적자 폭 축소로 실적 개선 기대");
        assertEquals("적자 폭 축소 → POSITIVE", SentimentTag.POSITIVE, r.getTag());
    }

    @Test
    public void 적자폭축소_공백없음_호재() {
        // 띄어쓰기 없는 형태도 동일하게 호재
        SentimentResult r = analyzer.analyze("적자폭 축소로 흑자 전환 가시화");
        assertEquals("적자폭 축소 → POSITIVE", SentimentTag.POSITIVE, r.getTag());
    }

    // ── 오탐 방지 ──────────────────────────────────────────────

    @Test
    public void 감자튀김_악재아님() {
        // "감자" 단독 키워드 없음 → "감자튀김"은 무상감자/유상감자 등에 해당 안 됨
        SentimentResult r = analyzer.analyze("감자튀김 프랜차이즈 상장");
        assertEquals("감자튀김 → POSITIVE 또는 NEUTRAL (악재 아님)",
                     false,
                     r.getTag() == SentimentTag.NEGATIVE);
    }

    // ── 중립 ──────────────────────────────────────────────────

    @Test
    public void 보합_중립() {
        SentimentResult r = analyzer.analyze("코스피 보합 마감");
        assertEquals("코스피 보합 마감 → NEUTRAL", SentimentTag.NEUTRAL, r.getTag());
    }

    // ── 판단 근거 키워드 반환 확인 ─────────────────────────────

    @Test
    public void 매칭키워드_비어있지않음() {
        SentimentResult r = analyzer.analyze("삼성전자 신고가 경신");
        assertEquals("POSITIVE", SentimentTag.POSITIVE, r.getTag());
        assertEquals("매칭 키워드 1개 이상", true, !r.getMatchedKeywords().isEmpty());
    }

    @Test
    public void 중립_매칭키워드_비어있음() {
        SentimentResult r = analyzer.analyze("코스피 보합 마감");
        assertEquals("중립 시 매칭 키워드 없음", true, r.getMatchedKeywords().isEmpty());
    }

    // ── null / 빈 문자열 방어 ─────────────────────────────────

    @Test
    public void null제목_중립반환() {
        SentimentResult r = analyzer.analyze(null);
        assertEquals("null → NEUTRAL", SentimentTag.NEUTRAL, r.getTag());
    }

    @Test
    public void 빈제목_중립반환() {
        SentimentResult r = analyzer.analyze("");
        assertEquals("빈 문자열 → NEUTRAL", SentimentTag.NEUTRAL, r.getTag());
    }
}

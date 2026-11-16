package org.mnu.news.sentiment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 규칙 기반 감성 분석기.
 * sentiment-dict.txt(UTF-8)를 읽어 금융 뉴스 제목의 호재/악재를 판별한다.
 *
 * <p>공백 정규화: 사전 키워드와 분석 대상 제목 모두 공백을 제거한 뒤 비교한다.
 * → "적자 축소", "적자축소", "적 자 축 소"가 동일 키워드로 매칭된다.
 * 화면 툴팁에는 사전 원문 키워드를 그대로 표시한다.
 *
 * <p>Longest-match: 키워드를 정규화 길이 내림차순으로 정렬하고 boolean[] used 배열로
 * 이미 매칭된 위치를 추적한다. 짧은 키워드가 긴 구문 안에 중복 매칭되지 않는다.
 */
@Component
public class SentimentAnalyzer implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(SentimentAnalyzer.class);

    private List<DictEntry> entries = Collections.emptyList();

    static final class DictEntry {
        final String originalKeyword;   // 사전 원문 (공백 포함) — 툴팁 표시용
        final String normalizedKeyword; // 공백 제거 + 소문자 — 매칭용
        final int    weight;

        DictEntry(String original, String normalized, int weight) {
            this.originalKeyword   = original;
            this.normalizedKeyword = normalized;
            this.weight            = weight;
        }
    }

    @Override
    public void afterPropertiesSet() {
        init();
    }

    public void init() {
        List<DictEntry> loaded = new ArrayList<DictEntry>();

        InputStream is = getClass().getClassLoader()
                                   .getResourceAsStream("sentiment-dict.txt");
        if (is == null) {
            logger.warn("SentimentAnalyzer: sentiment-dict.txt를 클래스패스에서 찾을 수 없습니다.");
            return;
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int comma = line.lastIndexOf(',');
                if (comma < 0) continue;

                String keyword   = line.substring(0, comma).trim();
                String weightStr = line.substring(comma + 1).trim();

                try {
                    int    weight     = Integer.parseInt(weightStr);
                    String normalized = normalize(keyword);
                    if (!normalized.isEmpty()) {
                        loaded.add(new DictEntry(keyword, normalized, weight));
                    }
                } catch (NumberFormatException e) {
                    logger.warn("SentimentAnalyzer: 가중치 파싱 실패 — {}", line);
                }
            }
        } catch (Exception e) {
            logger.error("SentimentAnalyzer: sentiment-dict.txt 로드 중 오류", e);
        }

        // 정규화 길이 내림차순 정렬 (longest-match-first)
        Collections.sort(loaded, new Comparator<DictEntry>() {
            @Override
            public int compare(DictEntry a, DictEntry b) {
                return b.normalizedKeyword.length() - a.normalizedKeyword.length();
            }
        });

        this.entries = Collections.unmodifiableList(loaded);
        logger.info("SentimentAnalyzer: sentiment-dict.txt 로드 완료 — {}개 키워드", loaded.size());
    }

    /**
     * 뉴스 제목의 감성을 분석한다.
     *
     * @param title 뉴스 기사 제목 (null 허용)
     * @return 감성 분석 결과 (null 반환 없음)
     */
    public SentimentResult analyze(String title) {
        if (title == null || title.isEmpty()) {
            return new SentimentResult(SentimentTag.NEUTRAL, 0,
                                       Collections.<String>emptyList());
        }

        String     normalizedTitle = normalize(title);
        boolean[]  used            = new boolean[normalizedTitle.length()];
        int        score           = 0;
        List<String> matched       = new ArrayList<String>();

        for (DictEntry entry : entries) {
            String kw  = entry.normalizedKeyword;
            int    kwLen = kw.length();
            if (kwLen > normalizedTitle.length()) continue;

            // 키워드당 첫 번째 미사용 위치에서만 1회 매칭
            int searchFrom = 0;
            while (searchFrom <= normalizedTitle.length() - kwLen) {
                int pos = normalizedTitle.indexOf(kw, searchFrom);
                if (pos < 0) break;

                // 매칭 범위 전체가 미사용인지 확인
                boolean canUse = true;
                for (int i = pos; i < pos + kwLen; i++) {
                    if (used[i]) { canUse = false; break; }
                }

                if (canUse) {
                    for (int i = pos; i < pos + kwLen; i++) used[i] = true;
                    score += entry.weight;
                    matched.add(entry.originalKeyword);
                    break; // 키워드당 1회 매칭
                }
                searchFrom = pos + 1;
            }
        }

        SentimentTag tag;
        if      (score > 0) tag = SentimentTag.POSITIVE;
        else if (score < 0) tag = SentimentTag.NEGATIVE;
        else                tag = SentimentTag.NEUTRAL;

        return new SentimentResult(tag, score, matched);
    }

    /** 공백 제거 + 소문자 변환 (한글·영문 혼용 제목 대응) */
    static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase();
    }
}

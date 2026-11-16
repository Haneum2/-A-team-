<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>뉴스 - pinmate</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
    <style>
        /* 감성 배지 */
        .badge { display:inline-block; padding:1px 7px; border-radius:3px;
                 font-size:.75rem; font-weight:600; cursor:default; }
        .badge-positive { background:#d4edda; color:#155724; }
        .badge-negative { background:#f8d7da; color:#721c24; }
        .badge-neutral  { background:#e2e3e5; color:#383d41; }
        /* 필터 버튼 활성 상태 */
        .filter-active { font-weight:700; text-decoration:underline; }
        /* 브리핑 카드 */
        .briefing-grid { display:flex; flex-wrap:wrap; gap:12px; margin-bottom:16px; }
        .briefing-card { border:1px solid #dee2e6; border-radius:6px; padding:12px 16px;
                         min-width:220px; flex:1; background:#fafafa; }
        .briefing-card h4 { margin:0 0 6px; font-size:.95rem; }
        .briefing-card .mood { font-size:.85rem; color:#495057; margin-bottom:4px; }
        .briefing-card .kws  { font-size:.8rem;  color:#6c757d; margin-bottom:4px; }
        .briefing-card .headline { font-size:.8rem; }
        /* 면책 안내 */
        .sentiment-disclaimer { font-size:.78rem; color:#6c757d; margin-top:12px;
                                 border-top:1px solid #dee2e6; padding-top:8px; }
    </style>
</head>
<body>
<header>
    <h1>pinmate</h1>
    <nav>
        <a href="${pageContext.request.contextPath}/">홈</a>
        <a href="${pageContext.request.contextPath}/mypage">마이페이지</a>
        <a href="${pageContext.request.contextPath}/stock/search">종목</a>
        <a href="${pageContext.request.contextPath}/watchlist">관심종목</a>
        <a href="${pageContext.request.contextPath}/portfolio">포트폴리오</a>
        <a href="${pageContext.request.contextPath}/news">뉴스</a>
        <a href="${pageContext.request.contextPath}/disclosure">공시</a>
        <a href="${pageContext.request.contextPath}/logout">로그아웃</a>
    </nav>
</header>

<main class="container wide">

    <%-- 탭 버튼 --%>
    <div class="card">
        <h2>뉴스</h2>
        <div class="tab-bar">
            <a href="${pageContext.request.contextPath}/news?tab=all"
               class="btn small<c:if test="${activeTab == 'all'}"> tab-active</c:if>">전체</a>
            <a href="${pageContext.request.contextPath}/news?tab=holding"
               class="btn small<c:if test="${activeTab == 'holding'}"> tab-active</c:if>">보유종목</a>
            <a href="${pageContext.request.contextPath}/news?tab=watch"
               class="btn small<c:if test="${activeTab == 'watch'}"> tab-active</c:if>">관심종목</a>
        </div>
    </div>

    <%-- 종목 없음 안내 (보유/관심 탭) --%>
    <c:if test="${not hasStocks}">
        <div class="card">
            <c:choose>
                <c:when test="${activeTab == 'holding'}">
                    <p class="info-msg">보유 종목을 추가하면 관련 뉴스를 볼 수 있습니다.
                        <a href="${pageContext.request.contextPath}/portfolio">포트폴리오</a>에서 종목을 등록해 보세요.</p>
                </c:when>
                <c:otherwise>
                    <p class="info-msg">관심종목을 추가하면 관련 뉴스를 볼 수 있습니다.
                        <a href="${pageContext.request.contextPath}/watchlist">관심종목</a>에서 종목을 등록해 보세요.</p>
                </c:otherwise>
            </c:choose>
        </div>
    </c:if>

    <%-- 갱신 실패 배너 --%>
    <c:if test="${stale}">
        <div class="stale-banner">
            뉴스 서버와의 연결이 일시적으로 끊겼습니다. 마지막으로 성공한 데이터를 표시합니다.
        </div>
    </c:if>

    <%-- 종목별 3줄 브리핑 카드 (보유/관심 탭) --%>
    <c:if test="${not empty briefings}">
        <div class="card">
            <h3 style="margin-bottom:10px;">종목별 뉴스 브리핑</h3>
            <div class="briefing-grid">
                <c:forEach items="${briefings}" var="b">
                    <div class="briefing-card">
                        <h4><c:out value="${b.stockName}"/></h4>
                        <p class="mood">
                            호재 <c:out value="${b.positiveCount}"/>
                            · 악재 <c:out value="${b.negativeCount}"/>
                            · 중립 <c:out value="${b.neutralCount}"/>
                            &nbsp;→&nbsp;<strong><c:out value="${b.overallMood}"/></strong>
                        </p>
                        <c:if test="${not empty b.topKeywords}">
                            <p class="kws">키워드:
                                <c:forEach items="${b.topKeywords}" var="kw" varStatus="st">
                                    <c:out value="${kw}"/><c:if test="${!st.last}">,&nbsp;</c:if>
                                </c:forEach>
                            </p>
                        </c:if>
                        <c:if test="${not empty b.headlineTitle}">
                            <p class="headline">대표:
                                <a href="<c:out value='${b.headlineLink}'/>"
                                   target="_blank" rel="noopener noreferrer">
                                    <c:out value="${b.headlineTitle}"/>
                                </a>
                            </p>
                        </c:if>
                    </div>
                </c:forEach>
            </div>
        </div>
    </c:if>

    <%-- 뉴스 카드: hasStocks 일 때만 표시. 필터 바는 결과 0건이어도 항상 표시 --%>
    <c:if test="${hasStocks}">
        <div class="card">

            <%-- 감성 필터 버튼 (항상 표시) --%>
            <div style="margin-bottom:10px;">
                <span style="font-size:.85rem; margin-right:6px;">감성 필터:</span>
                <a href="${pageContext.request.contextPath}/news?tab=<c:out value='${activeTab}'/>&amp;filter=all"
                   class="btn small<c:if test="${activeFilter == 'all'}"> filter-active</c:if>">전체</a>
                <a href="${pageContext.request.contextPath}/news?tab=<c:out value='${activeTab}'/>&amp;filter=positive"
                   class="btn small<c:if test="${activeFilter == 'positive'}"> filter-active</c:if>">호재만</a>
                <a href="${pageContext.request.contextPath}/news?tab=<c:out value='${activeTab}'/>&amp;filter=negative"
                   class="btn small<c:if test="${activeFilter == 'negative'}"> filter-active</c:if>">악재만</a>
            </div>

            <%-- 콘텐츠 영역 --%>
            <c:choose>
                <c:when test="${not empty news}">
                    <div class="table-scroll">
                        <table class="disclosure-table">
                            <thead>
                                <tr>
                                    <th class="col-date">발행 시각</th>
                                    <th class="col-corp">언론사</th>
                                    <th>제목</th>
                                    <th>감성</th>
                                    <th>종목</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach items="${news}" var="n">
                                    <tr>
                                        <td class="col-date">
                                            <c:choose>
                                                <c:when test="${not empty n.pubDate}">
                                                    <fmt:formatDate value="${n.pubDate}" pattern="yyyy-MM-dd HH:mm"/>
                                                </c:when>
                                                <c:otherwise>-</c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td class="col-corp"><c:out value="${n.source}"/></td>
                                        <td>
                                            <a href="<c:out value='${n.link}'/>"
                                               target="_blank" rel="noopener noreferrer">
                                                <c:out value="${n.title}"/>
                                            </a>
                                        </td>
                                        <td style="white-space:nowrap;">
                                            <c:choose>
                                                <c:when test="${n.sentiment.tag == 'POSITIVE'}">
                                                    <span class="badge badge-positive"
                                                          title="<c:out value='${n.sentiment.matchedKeywordsStr}'/>">호재</span>
                                                </c:when>
                                                <c:when test="${n.sentiment.tag == 'NEGATIVE'}">
                                                    <span class="badge badge-negative"
                                                          title="<c:out value='${n.sentiment.matchedKeywordsStr}'/>">악재</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="badge badge-neutral">중립</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td class="col-badges">
                                            <c:forEach items="${n.relatedStocks}" var="st">
                                                <span class="tag"><c:out value="${st}"/></span>
                                            </c:forEach>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>

                    <p class="dart-footer">
                        <c:if test="${not empty asOfStr}">
                            뉴스 기준 시각: <c:out value="${asOfStr}"/><br>
                        </c:if>
                        <c:if test="${stale}">
                            일부 피드 갱신에 실패했습니다. 잠시 후 다시 확인해 주세요.<br>
                        </c:if>
                        뉴스 저작권은 각 언론사에 있으며 제목·언론사·발행 시각·원문 링크만 제공합니다.
                    </p>
                </c:when>
                <c:when test="${activeFilter == 'positive'}">
                    <%-- 호재 필터 0건: 전체 보기 링크 제공 --%>
                    <p class="info-msg">호재로 분류된 기사가 없습니다.
                        <a href="${pageContext.request.contextPath}/news?tab=<c:out value='${activeTab}'/>&amp;filter=all">전체 보기</a>
                    </p>
                </c:when>
                <c:when test="${activeFilter == 'negative'}">
                    <%-- 악재 필터 0건: 전체 보기 링크 제공 --%>
                    <p class="info-msg">악재로 분류된 기사가 없습니다.
                        <a href="${pageContext.request.contextPath}/news?tab=<c:out value='${activeTab}'/>&amp;filter=all">전체 보기</a>
                    </p>
                </c:when>
                <c:otherwise>
                    <p class="info-msg">표시할 뉴스가 없습니다.</p>
                </c:otherwise>
            </c:choose>

            <%-- 면책 안내 (항상 표시) --%>
            <p class="sentiment-disclaimer">
                ※ 호재/악재 태그와 브리핑은 기사 제목의 키워드를 기반으로 자동 분류한 참고 정보이며
                투자 판단의 근거가 될 수 없습니다.
            </p>
        </div>
    </c:if>

</main>
</body>
</html>

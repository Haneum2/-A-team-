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
        <a href="${pageContext.request.contextPath}/notifications">알림<c:if test="${unreadNotiCount > 0}"><span class="noti-badge">${unreadNotiCount > 99 ? '99+' : unreadNotiCount}</span></c:if></a>
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

    <%-- 뉴스 목록 --%>
    <c:choose>
        <c:when test="${not empty news}">
            <div class="card">
                <div class="table-scroll">
                    <table class="disclosure-table">
                        <thead>
                            <tr>
                                <th class="col-date">발행 시각</th>
                                <th class="col-corp">언론사</th>
                                <th>제목</th>
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
            </div>
        </c:when>
        <c:when test="${hasStocks}">
            <div class="card">
                <p class="info-msg">표시할 뉴스가 없습니다.</p>
            </div>
        </c:when>
    </c:choose>

</main>
</body>
</html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>관심종목 - pinmate</title>
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
			<a href="${pageContext.request.contextPath}/disclosure">공시</a>
			<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
		</nav>
	</header>

	<main class="container wide">
		<h2>${sessionScope.loginUser}님의 관심종목</h2>

		<c:if test="${not empty flashMsg}"><p class="success"><c:out value="${flashMsg}"/></p></c:if>
		<c:if test="${not empty flashError}"><p class="error"><c:out value="${flashError}"/></p></c:if>
		<c:if test="${not empty error}"><p class="error"><c:out value="${error}"/></p></c:if>

		<c:if test="${not empty view}">
			<div class="card">
				<c:choose>
					<c:when test="${view.noItems}">
						<p class="info-msg">아직 관심종목이 없습니다.
							<a href="${pageContext.request.contextPath}/stock/search">종목을 검색</a>해서 상세 화면에서 추가해 보세요.</p>
					</c:when>
					<c:otherwise>
						<c:if test="${view.stale}">
							<div class="stale-banner">시세 서버와 연결이 불안정해 마지막으로 받은 시세를 표시합니다.</div>
						</c:if>
						<c:if test="${not empty view.errorMsg}">
							<p class="error"><c:out value="${view.errorMsg}"/></p>
						</c:if>

						<div class="table-scroll">
							<table class="disclosure-table portfolio-table">
								<thead>
									<tr>
										<th>종목</th>
										<th class="num">현재가</th>
										<th class="num">전일 대비</th>
										<th class="num">등락률</th>
										<th></th>
									</tr>
								</thead>
								<tbody>
									<c:forEach items="${view.items}" var="it">
										<tr>
											<td>
												<a href="${pageContext.request.contextPath}/stock/<c:out value='${it.stockCode}'/>"><c:out value="${it.stockName}"/></a>
												<br><small class="muted"><c:out value="${it.stockCode}"/></small>
											</td>
											<c:choose>
												<c:when test="${it.priced}">
													<td class="num"><fmt:formatNumber value="${it.price}" pattern="#,##0.##"/></td>
												</c:when>
												<c:otherwise>
													<td class="num muted">-</td>
												</c:otherwise>
											</c:choose>
											<c:choose>
												<c:when test="${it.hasChange}">
													<td class="num ${it.changeClass}"><fmt:formatNumber value="${it.change}" pattern="+#,##0.##;-#,##0.##"/></td>
													<td class="num ${it.changeClass}"><fmt:formatNumber value="${it.changeRate}" pattern="+0.00;-0.00"/>%</td>
												</c:when>
												<c:otherwise>
													<td class="num muted">-</td>
													<td class="num muted">-</td>
												</c:otherwise>
											</c:choose>
											<td class="actions">
												<form method="post" action="${pageContext.request.contextPath}/watchlist/delete">
													<input type="hidden" name="watchId" value="${it.watchId}">
													<button type="submit" class="btn small danger"
														onclick="return confirm('관심종목에서 삭제할까요?');">삭제</button>
												</form>
											</td>
										</tr>
									</c:forEach>
								</tbody>
							</table>
						</div>

						<p class="dart-footer">
							<c:if test="${not empty view.asOfText}">시세 기준 시각: <c:out value="${view.asOfText}"/><br></c:if>
							전일 대비는 전일 종가 기준입니다. 출처: 토스증권 Open API
						</p>
					</c:otherwise>
				</c:choose>
			</div>
		</c:if>
	</main>
</body>
</html>

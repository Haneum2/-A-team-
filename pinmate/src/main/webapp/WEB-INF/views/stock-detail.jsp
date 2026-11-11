<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title><c:out value="${not empty corp ? corp.corpName : '종목 상세'}"/> - pinmate</title>
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
		<a href="${pageContext.request.contextPath}/disclosure">공시</a>
		<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
	</nav>
</header>

<main class="container">

	<p><a href="${pageContext.request.contextPath}/stock/search" class="back-link">&larr; 종목 검색</a></p>

	<c:if test="${not empty flashMsg}"><p class="success"><c:out value="${flashMsg}"/></p></c:if>
	<c:if test="${not empty flashError}"><p class="error"><c:out value="${flashError}"/></p></c:if>

	<c:choose>
		<c:when test="${apiKeyMissing}">
			<div class="card">
				<p class="error">DART API 키가 설정되지 않았습니다.
					<code>src/main/resources/api.properties</code>에
					<code>dart.api.key</code>를 입력 후 서버를 재시작하세요.</p>
			</div>
		</c:when>

		<c:when test="${notFound}">
			<div class="card">
				<p class="error">종목코드 '<c:out value="${stockCode}"/>'에 해당하는 상장 종목을 찾을 수 없습니다.</p>
			</div>
		</c:when>

		<c:otherwise>
			<%-- 종목 기본 정보 + 시세 --%>
			<div class="card">
				<h2 class="stock-title">
					<c:out value="${corp.corpName}"/>
					<span class="stock-code"><c:out value="${corp.stockCode}"/></span>
				</h2>
				<form method="post" action="${pageContext.request.contextPath}/watchlist/add">
					<input type="hidden" name="stockCode" value="<c:out value='${corp.stockCode}'/>">
					<button type="submit" class="btn small">관심종목 추가</button>
				</form>
				<%-- 시세: 데이터 이용 조건(KRX 원천 데이터 제3자 제공 제한) 확인 후 연결 예정 --%>
				<p class="info-msg">시세 정보는 준비 중입니다.</p>
			</div>

			<%-- 최근 공시 --%>
			<div class="card">
				<h2>최근 공시</h2>

				<c:if test="${not empty dartError}">
					<p class="error"><c:out value="${dartError}"/></p>
				</c:if>

				<c:if test="${empty dartError}">
					<c:if test="${stale}">
						<div class="stale-banner">
							DART 서버와의 연결이 일시적으로 끊겼습니다. 마지막으로 성공한 데이터를 표시합니다.
						</div>
					</c:if>

					<c:choose>
						<c:when test="${not empty disclosures}">
							<table class="disclosure-table">
								<tbody>
									<c:forEach items="${disclosures}" var="d">
										<tr>
											<td class="col-date"><c:out value="${d.receiptDate}"/></td>
											<td>
												<a href="<c:out value='${d.dartUrl}'/>" target="_blank" rel="noopener noreferrer">
													<c:out value="${d.title}"/>
												</a>
											</td>
											<td class="col-badge">
												<c:if test="${d.key}"><span class="key-badge">핵심</span></c:if>
											</td>
										</tr>
									</c:forEach>
								</tbody>
							</table>
							<p class="more-link">
								<a href="${pageContext.request.contextPath}/disclosure?stockCode=<c:out value='${corp.stockCode}'/>">
									공시 전체 보기 (최근 3개월 <c:out value="${disclosureTotal}"/>건) &rarr;
								</a>
							</p>
						</c:when>
						<c:otherwise>
							<p class="info-msg">최근 3개월 내 공시 내역이 없습니다.</p>
						</c:otherwise>
					</c:choose>

					<p class="dart-footer">
						<c:if test="${not empty asOfStr}">
							DART 기준 시각: <c:out value="${asOfStr}"/><br>
						</c:if>
						출처: 금융감독원 전자공시시스템(DART)
					</p>
				</c:if>
			</div>
		</c:otherwise>
	</c:choose>

</main>
</body>
</html>

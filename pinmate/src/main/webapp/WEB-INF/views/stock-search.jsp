<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>종목 검색 - pinmate</title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
</head>
<body>
<header>
	<h1>pinmate</h1>
	<nav>
		<a href="${pageContext.request.contextPath}/">홈</a>
		<a href="${pageContext.request.contextPath}/mypage">마이페이지</a>
		<a href="${pageContext.request.contextPath}/stock/search">종목</a>
		<a href="${pageContext.request.contextPath}/disclosure">공시</a>
		<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
	</nav>
</header>

<main class="container">

	<c:if test="${apiKeyMissing}">
		<div class="card">
			<p class="error">DART API 키가 설정되지 않았습니다.
				<code>src/main/resources/api.properties</code>에
				<code>dart.api.key</code>를 입력 후 서버를 재시작하세요.</p>
		</div>
	</c:if>

	<c:if test="${not apiKeyMissing}">
		<div class="card">
			<h2>종목 검색</h2>
			<form method="get" action="${pageContext.request.contextPath}/stock/search" class="search-form">
				<input type="text" name="query" value="<c:out value='${query}'/>"
					   placeholder="종목명 또는 종목코드 (예: 삼성전자, 005930)">
				<button type="submit" class="btn">검색</button>
			</form>

			<c:if test="${not empty searchResults}">
				<ul class="corp-list">
					<c:forEach items="${searchResults}" var="corp">
						<li>
							<a href="${pageContext.request.contextPath}/stock/<c:out value='${corp.stockCode}'/>">
								<c:out value="${corp.corpName}"/> (<c:out value="${corp.stockCode}"/>)
							</a>
						</li>
					</c:forEach>
				</ul>
			</c:if>

			<%-- UC-005 2a: 검색 결과 없음 --%>
			<c:if test="${not empty query and empty searchResults}">
				<p class="info-msg">'<c:out value="${query}"/>'에 대한 검색 결과가 없습니다. 종목명이나 6자리 종목코드를 확인해 주세요.</p>
			</c:if>
			<c:if test="${empty query}">
				<p class="info-msg">종목명 일부나 종목코드로 검색할 수 있습니다.</p>
			</c:if>
		</div>
	</c:if>

</main>
</body>
</html>

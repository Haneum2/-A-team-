<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>공시 조회 - pinmate</title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
</head>
<body>
<header>
	<h1>pinmate</h1>
	<nav>
		<a href="${pageContext.request.contextPath}/">홈</a>
		<a href="${pageContext.request.contextPath}/mypage">마이페이지</a>
		<a href="${pageContext.request.contextPath}/portfolio">포트폴리오</a>
		<a href="${pageContext.request.contextPath}/disclosure">공시</a>
		<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
	</nav>
</header>

<main class="container">

	<%-- API 키 미설정 --%>
	<c:if test="${apiKeyMissing}">
		<div class="card">
			<p class="error">DART API 키가 설정되지 않았습니다.
				<code>src/main/resources/api.properties</code>에
				<code>dart.api.key</code>를 입력 후 서버를 재시작하세요.</p>
		</div>
	</c:if>

	<c:if test="${not apiKeyMissing}">

		<%-- 검색 카드 --%>
		<div class="card">
			<h2>공시 조회</h2>
			<form method="get" action="${pageContext.request.contextPath}/disclosure" class="search-form">
				<input type="text" name="query" value="<c:out value='${query}'/>"
					   placeholder="종목명 또는 종목코드 검색 (예: 삼성전자, 005930)">
				<button type="submit" class="btn">검색</button>
			</form>

			<%-- 종목 검색 결과 --%>
			<c:if test="${not empty searchResults}">
				<ul class="corp-list">
					<c:forEach items="${searchResults}" var="corp">
						<li>
							<a href="${pageContext.request.contextPath}/disclosure?stockCode=<c:out value='${corp.stockCode}'/>">
								<c:out value="${corp.corpName}"/> (<c:out value="${corp.stockCode}"/>)
							</a>
						</li>
					</c:forEach>
				</ul>
			</c:if>
			<c:if test="${not empty query and empty searchResults}">
				<p class="info-msg">검색 결과가 없습니다.</p>
			</c:if>
		</div>

		<%-- 공시 목록 카드 --%>
		<c:choose>
			<c:when test="${empty stockCode and empty query}">
				<%-- 초기 화면 --%>
				<div class="card">
					<p class="info-msg">종목명 또는 종목코드를 검색해 주세요.</p>
				</div>
			</c:when>
			<c:when test="${not empty stockCode}">
				<div class="card">

					<%-- DART API 오류 --%>
					<c:if test="${not empty dartError}">
						<p class="error"><c:out value="${dartError}"/></p>
					</c:if>

					<c:if test="${empty dartError}">

						<%-- 갱신 실패 배너 --%>
						<c:if test="${stale}">
							<div class="stale-banner">
								DART 서버와의 연결이 일시적으로 끊겼습니다. 마지막으로 성공한 데이터를 표시합니다.
							</div>
						</c:if>

						<%-- 핵심 공시 필터 --%>
						<form method="get" action="${pageContext.request.contextPath}/disclosure" class="filter-form">
							<input type="hidden" name="stockCode" value="<c:out value='${stockCode}'/>">
							<label>
								<input type="checkbox" name="keyOnly" value="true"
									   <c:if test="${keyOnly}">checked</c:if>
									   onchange="this.form.submit()">
								핵심 공시만 보기
							</label>
						</form>

						<%-- 공시 테이블 --%>
						<c:choose>
							<c:when test="${not empty disclosures}">
								<table class="disclosure-table">
									<thead>
										<tr>
											<th>날짜</th>
											<th>회사명</th>
											<th>공시 제목</th>
											<th></th>
										</tr>
									</thead>
									<tbody>
										<c:forEach items="${disclosures}" var="d">
											<tr>
												<td class="col-date"><c:out value="${d.receiptDate}"/></td>
												<td class="col-corp"><c:out value="${d.corpName}"/></td>
												<td>
													<c:choose>
														<c:when test="${not empty d.rcpNo}">
															<a href="<c:out value='${d.dartUrl}'/>" target="_blank" rel="noopener noreferrer">
																<c:out value="${d.title}"/>
															</a>
														</c:when>
														<c:otherwise>
															<c:out value="${d.title}"/>
														</c:otherwise>
													</c:choose>
												</td>
												<td class="col-badge">
													<c:if test="${d.key}">
														<span class="key-badge">핵심</span>
													</c:if>
												</td>
											</tr>
										</c:forEach>
									</tbody>
								</table>
							</c:when>
							<c:otherwise>
								<p class="info-msg">최근 3개월 내 공시 내역이 없습니다.</p>
							</c:otherwise>
						</c:choose>

						<%-- 기준 시각 & 출처 --%>
						<p class="dart-footer">
							<c:if test="${not empty asOfStr}">
								DART 기준 시각: <c:out value="${asOfStr}"/><br>
							</c:if>
							출처: 금융감독원 전자공시시스템(DART)
						</p>

					</c:if><%-- end empty dartError --%>
				</div>
			</c:when>
		</c:choose>

	</c:if><%-- end not apiKeyMissing --%>

</main>
</body>
</html>

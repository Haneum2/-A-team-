<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>포트폴리오 - pinmate</title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
</head>
<body>
	<header>
		<h1>pinmate</h1>
		<nav>
			<a href="${pageContext.request.contextPath}/">홈</a>
			<a href="${pageContext.request.contextPath}/mypage">마이페이지</a>
			<a href="${pageContext.request.contextPath}/watchlist">관심종목</a>
			<a href="${pageContext.request.contextPath}/portfolio">포트폴리오</a>
			<a href="${pageContext.request.contextPath}/disclosure">공시</a>
			<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
		</nav>
	</header>

	<main class="container wide">
		<h2>${sessionScope.loginUser}님의 포트폴리오</h2>

		<c:if test="${not empty flashMsg}"><p class="success"><c:out value="${flashMsg}"/></p></c:if>
		<c:if test="${not empty flashError}"><p class="error"><c:out value="${flashError}"/></p></c:if>
		<c:if test="${not empty error}"><p class="error"><c:out value="${error}"/></p></c:if>

		<c:if test="${not empty summary}">

			<%-- 요약: 총 평가금액 / 총 손익 --%>
			<div class="card">
				<h2>요약</h2>
				<c:choose>
					<c:when test="${summary.noHoldings}">
						<p class="info-msg">아직 등록한 종목이 없습니다. 아래에서 종목을 검색해 추가해 주세요.</p>
					</c:when>
					<c:otherwise>
						<c:if test="${summary.stale}">
							<div class="stale-banner">시세 서버와 연결이 불안정해 마지막으로 받은 시세를 표시합니다.</div>
						</c:if>
						<c:if test="${not summary.hasPrice}">
							<p class="error">현재가를 불러오지 못해 평가금액과 손익을 계산할 수 없습니다.
								<c:if test="${not empty summary.errorMsg}"><br><c:out value="${summary.errorMsg}"/></c:if>
							</p>
						</c:if>
						<c:if test="${summary.hasPrice}">
							<div class="summary-grid">
								<div><span class="label">총 평가금액</span>
									<strong><fmt:formatNumber value="${summary.totalEval}" pattern="#,##0"/>원</strong></div>
								<div><span class="label">총 매입금액</span>
									<strong><fmt:formatNumber value="${summary.totalCost}" pattern="#,##0"/>원</strong></div>
								<div><span class="label">총 손익</span>
									<strong class="${summary.totalProfitClass}">
										<fmt:formatNumber value="${summary.totalProfit}" pattern="+#,##0;-#,##0"/>원
										(<fmt:formatNumber value="${summary.totalRate}" pattern="+0.00;-0.00"/>%)
									</strong></div>
							</div>
							<c:if test="${summary.unpricedCount > 0}">
								<p class="info-msg">현재가를 불러오지 못한 종목 ${summary.unpricedCount}개는 합계에서 제외했습니다.</p>
							</c:if>
							<p class="dart-footer">
								<c:if test="${not empty summary.asOfText}">시세 기준 시각: <c:out value="${summary.asOfText}"/><br></c:if>
								출처: 토스증권 Open API
							</p>
						</c:if>
					</c:otherwise>
				</c:choose>
			</div>

			<%-- 보유 종목 목록 --%>
			<c:if test="${not summary.noHoldings}">
				<div class="card">
					<h2>보유 종목</h2>
					<div class="table-scroll">
						<table class="disclosure-table portfolio-table">
							<thead>
								<tr>
									<th>종목</th>
									<th>섹터 / 테마</th>
									<th class="num">수량</th>
									<th class="num">평단가</th>
									<th class="num">현재가</th>
									<th class="num">평가금액</th>
									<th class="num">손익</th>
									<th class="num">수익률</th>
									<th></th>
								</tr>
							</thead>
							<tbody>
								<c:forEach items="${summary.items}" var="it">
									<tr>
										<td><c:out value="${it.stockName}"/><br><small class="muted"><c:out value="${it.stockCode}"/></small></td>
										<td><span class="tag"><c:out value="${it.sector}"/></span>
											<c:if test="${not empty it.theme}"><br><small class="muted"><c:out value="${it.theme}"/></small></c:if></td>
										<td class="num"><input class="mini" type="text" name="quantity" form="f-${it.holdingId}"
											value="<fmt:formatNumber value="${it.quantity}" pattern="0.####" groupingUsed="false"/>"></td>
										<td class="num"><input class="mini" type="text" name="avgPrice" form="f-${it.holdingId}"
											value="<fmt:formatNumber value="${it.avgPrice}" pattern="0.####" groupingUsed="false"/>"></td>
										<c:choose>
											<c:when test="${it.priced}">
												<td class="num"><fmt:formatNumber value="${it.currentPrice}" pattern="#,##0.##"/></td>
												<td class="num"><fmt:formatNumber value="${it.evalAmount}" pattern="#,##0"/></td>
												<td class="num ${it.profitClass}"><fmt:formatNumber value="${it.profit}" pattern="+#,##0;-#,##0"/></td>
												<td class="num ${it.profitClass}"><fmt:formatNumber value="${it.profitRate}" pattern="+0.00;-0.00"/>%</td>
											</c:when>
											<c:otherwise>
												<td class="num muted">-</td>
												<td class="num muted">-</td>
												<td class="num muted">-</td>
												<td class="num muted">-</td>
											</c:otherwise>
										</c:choose>
										<td class="actions">
											<form id="f-${it.holdingId}" method="post" action="${pageContext.request.contextPath}/portfolio/update">
												<input type="hidden" name="holdingId" value="${it.holdingId}">
												<button type="submit" class="btn small">수정</button>
												<button type="submit" class="btn small danger"
													formaction="${pageContext.request.contextPath}/portfolio/delete"
													onclick="return confirm('이 종목을 삭제할까요?');">삭제</button>
											</form>
										</td>
									</tr>
								</c:forEach>
							</tbody>
						</table>
					</div>
				</div>
			</c:if>

			<%-- 섹터별 비중 (UC-012) --%>
			<div class="card">
				<h2>섹터별 비중</h2>
				<c:choose>
					<c:when test="${summary.noHoldings}">
						<p class="info-msg">보유 종목이 없습니다. 아래에서 종목을 검색해 포트폴리오에 등록하면 섹터별 비중을 볼 수 있어요.</p>
					</c:when>
					<c:when test="${not summary.hasPrice}">
						<p class="info-msg">현재가를 불러오지 못해 섹터별 비중을 계산할 수 없습니다.</p>
					</c:when>
					<c:otherwise>
						<c:forEach items="${summary.sectorWeights}" var="w">
							<div class="score-row">
								<span class="name wide-name"><c:out value="${w.sector}"/></span>
								<span class="bar"><span style="width:${w.percent}%"></span></span>
								<span class="num wide-num"><fmt:formatNumber value="${w.percent}" pattern="0.0"/>%</span>
							</div>
						</c:forEach>
						<p class="info-msg">평가금액 기준이며, 섹터 정보가 없는 종목은 '미분류'로 묶었습니다.</p>
					</c:otherwise>
				</c:choose>
			</div>

		</c:if>

		<%-- 종목 검색 후 추가 --%>
		<div class="card">
			<h2>종목 추가</h2>
			<form method="get" action="${pageContext.request.contextPath}/portfolio" class="search-form">
				<input type="text" name="q" value="<c:out value='${q}'/>" placeholder="종목명 또는 종목코드 검색 (예: 삼성전자, 005930)">
				<button type="submit" class="btn">검색</button>
			</form>

			<c:if test="${not empty q}">
				<c:choose>
					<c:when test="${not searchAvailable}">
						<p class="error">종목 검색을 사용할 수 없습니다. (DART API 키 설정이 필요합니다)</p>
					</c:when>
					<c:when test="${empty searchResults}">
						<p class="info-msg">검색 결과가 없습니다.</p>
					</c:when>
					<c:otherwise>
						<ul class="corp-list">
							<c:forEach items="${searchResults}" var="r">
								<li>
									<form method="post" action="${pageContext.request.contextPath}/portfolio/add" class="add-form">
										<input type="hidden" name="stockCode" value="<c:out value='${r.stockCode}'/>">
										<span class="who"><strong><c:out value="${r.stockName}"/></strong>
											<small class="muted"><c:out value="${r.stockCode}"/></small><br>
											<span class="tag"><c:out value="${r.sector}"/></span>
											<c:if test="${not empty r.theme}"><small class="muted"><c:out value="${r.theme}"/></small></c:if>
										</span>
										<input class="mini" type="text" name="quantity" placeholder="수량">
										<input class="mini" type="text" name="avgPrice" placeholder="평단가(원)">
										<button type="submit" class="btn small">추가</button>
									</form>
								</li>
							</c:forEach>
						</ul>
					</c:otherwise>
				</c:choose>
			</c:if>
		</div>
	</main>
</body>
</html>

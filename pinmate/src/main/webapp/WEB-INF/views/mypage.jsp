<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>마이페이지</title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
</head>
<body>
	<header>
		<h1>pinmate</h1>
		<nav>
			<a href="${pageContext.request.contextPath}/">홈</a>
			<a href="${pageContext.request.contextPath}/mypage">마이페이지</a>
			<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
		</nav>
	</header>

	<main class="container">
		<h2>${sessionScope.loginUser}님의 마이페이지</h2>

		<c:if test="${param.diagnosed != null}">
			<p class="success">투자성향 진단 결과가 저장되었습니다.</p>
		</c:if>
		<c:if test="${not empty error}">
			<p class="error">${error}</p>
		</c:if>

		<div class="card">
			<h2>나의 투자성향</h2>
			<c:choose>
				<c:when test="${not empty latest}">
					<p class="type-badge">${latest.type.label}</p>
					<p>${latest.type.description}</p>
					<c:forEach var="s" items="${latest.scoreList}">
						<div class="score-row">
							<span class="name">${s.type.label}</span>
							<span class="bar"><span style="width:${s.score * 100 / latest.total}%"></span></span>
							<span class="num">${s.score}점</span>
						</div>
					</c:forEach>
					<p><small>진단일: <fmt:formatDate value="${latest.diagDate}" pattern="yyyy-MM-dd HH:mm" /></small></p>
				</c:when>
				<c:otherwise>
					<p>아직 진단 결과가 없습니다.</p>
				</c:otherwise>
			</c:choose>
			<a class="btn" href="${pageContext.request.contextPath}/survey">${empty latest ? '진단하기' : '재진단하기'}</a>
		</div>

		<c:if test="${history.size() > 1}">
			<div class="card">
				<h2>진단 이력</h2>
				<table class="history">
					<tr><th>진단일</th><th>투자성향</th></tr>
					<c:forEach var="h" items="${history}">
						<tr>
							<td><fmt:formatDate value="${h.diagDate}" pattern="yyyy-MM-dd HH:mm" /></td>
							<td>${h.type.label}</td>
						</tr>
					</c:forEach>
				</table>
			</div>
		</c:if>
	</main>
</body>
</html>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>투자성향 재진단</title>
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
		<div class="card login-box" style="width:auto">
			<h2>투자성향 재진단</h2>

			<c:if test="${not empty error}">
				<p class="error">${error}</p>
			</c:if>

			<form action="${pageContext.request.contextPath}/survey" method="post">
				<%@ include file="survey-fields.jspf" %>
				<button type="submit">진단 결과 저장</button>
			</form>
		</div>
	</main>
</body>
</html>

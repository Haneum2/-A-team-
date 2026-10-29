<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Home</title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
</head>
<body>
	<header>
		<h1>Example</h1>
		<nav>
			<a href="${pageContext.request.contextPath}/">홈</a>
			<a href="${pageContext.request.contextPath}/mypage">마이페이지</a>
			<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
		</nav>
	</header>

	<main>
		<h2>${sessionScope.loginUser}님, 환영합니다.</h2>
		<p>The time on the server is ${serverTime}.</p>
	</main>

	<footer>
		<small>&copy; Example</small>
	</footer>
</body>
</html>

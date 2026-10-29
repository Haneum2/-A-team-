<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>로그인</title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
</head>
<body class="login-page">
	<main class="login-box">
		<h1>로그인</h1>

		<c:if test="${param.registered != null}">
			<p class="success">회원가입이 완료되었습니다. 로그인해 주세요.</p>
		</c:if>

		<c:if test="${not empty error}">
			<p class="error">${error}</p>
		</c:if>

		<form action="${pageContext.request.contextPath}/login" method="post">
			<label for="userId">아이디</label>
			<input type="text" id="userId" name="userId" required autofocus>

			<label for="password">비밀번호</label>
			<input type="password" id="password" name="password" required minlength="8"
				pattern="(?=.*[^A-Za-z0-9\s]).{8,}"
				title="8자리 이상, 특수문자를 포함해야 합니다.">

			<button type="submit">로그인</button>
		</form>

		<p class="link"><a href="${pageContext.request.contextPath}/register">회원가입</a></p>
	</main>
</body>
</html>

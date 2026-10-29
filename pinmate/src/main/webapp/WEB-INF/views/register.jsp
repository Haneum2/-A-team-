<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>회원가입</title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/resources/css/style.css">
</head>
<body class="login-page">
	<main class="login-box">
		<h1>회원가입</h1>

		<c:if test="${not empty error}">
			<p class="error">${error}</p>
		</c:if>

		<form action="${pageContext.request.contextPath}/register" method="post">
			<label for="userId">아이디</label>
			<input type="text" id="userId" name="userId" required autofocus>

			<label for="password">비밀번호 (8자리 이상, 특수문자 포함)</label>
			<input type="password" id="password" name="password" required minlength="8"
				pattern="(?=.*[^A-Za-z0-9\s]).{8,}"
				title="8자리 이상, 특수문자를 포함해야 합니다.">

			<label for="passwordConfirm">비밀번호 확인</label>
			<input type="password" id="passwordConfirm" name="passwordConfirm" required>

			<button type="submit">가입하기</button>
		</form>

		<p class="link"><a href="${pageContext.request.contextPath}/login">로그인으로 돌아가기</a></p>
	</main>
</body>
</html>

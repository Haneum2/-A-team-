<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>알림 설정 - pinmate</title>
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
			<a href="${pageContext.request.contextPath}/news">뉴스</a>
			<a href="${pageContext.request.contextPath}/disclosure">공시</a>
			<a href="${pageContext.request.contextPath}/notifications">알림<c:if test="${unreadNotiCount > 0}"><span class="noti-badge">${unreadNotiCount > 99 ? '99+' : unreadNotiCount}</span></c:if></a>
			<a href="${pageContext.request.contextPath}/logout">로그아웃</a>
		</nav>
	</header>

	<main class="container">
		<a class="back-link" href="${pageContext.request.contextPath}/notifications">← 알림함</a>
		<h2>알림 설정</h2>

		<c:if test="${not empty flashMsg}"><p class="success"><c:out value="${flashMsg}"/></p></c:if>
		<c:if test="${not empty flashError}"><p class="error"><c:out value="${flashError}"/></p></c:if>
		<c:if test="${not empty error}"><p class="error"><c:out value="${error}"/></p></c:if>

		<c:if test="${not empty setting}">
			<form class="card" method="post" action="${pageContext.request.contextPath}/notifications/settings">
				<p class="info-msg" style="margin:0 0 12px">포트폴리오에 등록한 보유 종목을 기준으로 알려 드립니다.</p>

				<label class="toggle-row">
					<span>
						<strong>뉴스 알림</strong>
						<small>보유 종목 관련 새 기사가 올라오면 바로 알려 드려요.</small>
					</span>
					<input type="checkbox" name="news" value="Y" ${setting.news ? 'checked' : ''}>
				</label>
				<label class="toggle-row">
					<span>
						<strong>급등락 알림</strong>
						<small>장중 보유 종목이 전일 종가 대비 ±5% 이상 움직이면 알려 드려요. (종목별 하루 한 번)</small>
					</span>
					<input type="checkbox" name="price" value="Y" ${setting.price ? 'checked' : ''}>
				</label>
				<label class="toggle-row">
					<span>
						<strong>정기 리포트</strong>
						<small>평일 08:30 모닝 브리핑, 15:40 보유 종목 마감 시황을 보내 드려요.</small>
					</span>
					<input type="checkbox" name="report" value="Y" ${setting.report ? 'checked' : ''}>
				</label>

				<button type="submit" class="btn">저장</button>
			</form>
		</c:if>
	</main>
</body>
</html>

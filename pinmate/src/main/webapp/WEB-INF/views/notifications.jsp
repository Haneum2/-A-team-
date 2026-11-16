<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>알림 - pinmate</title>
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
		<div class="noti-head">
			<h2>알림</h2>
			<div class="noti-head-actions">
				<c:if test="${unreadNotiCount > 0}">
					<form method="post" action="${pageContext.request.contextPath}/notifications/read-all">
						<button type="submit" class="btn small">모두 읽음</button>
					</form>
				</c:if>
				<a class="btn small" href="${pageContext.request.contextPath}/notifications/settings">알림 설정</a>
			</div>
		</div>

		<c:if test="${not empty flashMsg}"><p class="success"><c:out value="${flashMsg}"/></p></c:if>
		<c:if test="${not empty flashError}"><p class="error"><c:out value="${flashError}"/></p></c:if>
		<c:if test="${not empty error}"><p class="error"><c:out value="${error}"/></p></c:if>

		<c:if test="${empty error}">
			<div class="card">
				<c:choose>
					<c:when test="${empty items}">
						<p class="info-msg">아직 받은 알림이 없습니다.
							<a href="${pageContext.request.contextPath}/portfolio">포트폴리오</a>에 보유 종목을 등록하면
							관련 뉴스·급등락·장 전후 리포트를 알려 드립니다.</p>
					</c:when>
					<c:otherwise>
						<ul class="noti-list">
							<c:forEach items="${items}" var="n">
								<li class="${n.read ? '' : 'unread'}">
									<a href="${pageContext.request.contextPath}/notifications/${n.notiId}/open"
										<c:if test="${not empty n.link and not n.internalLink}">target="_blank" rel="noopener"</c:if>>
										<span class="noti-type noti-${n.type}">${n.type.label}</span>
										<span class="noti-title"><c:out value="${n.title}"/></span>
										<c:if test="${not empty n.content}">
											<span class="noti-content"><c:out value="${n.content}"/></span>
										</c:if>
										<small class="muted"><fmt:formatDate value="${n.sentAt}" pattern="MM-dd HH:mm"/></small>
									</a>
								</li>
							</c:forEach>
						</ul>
						<p class="dart-footer">최근 알림 50건까지 보여 줍니다.</p>
					</c:otherwise>
				</c:choose>
			</div>
		</c:if>
	</main>
</body>
</html>

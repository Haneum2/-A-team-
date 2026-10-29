package org.mnu.dao;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * MEMBER 테이블 접근.
 */
@Repository
public class MemberDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** 저장된 비밀번호 해시를 반환한다. 없는 아이디면 null. */
	public String findPasswordHash(String userId) {
		List<String> list = jdbcTemplate.queryForList(
				"SELECT PASSWORD FROM MEMBER WHERE USER_ID = ?", String.class, userId);
		return list.isEmpty() ? null : list.get(0);
	}

	/** 회원 등록. 아이디가 중복이면 DuplicateKeyException 이 발생한다. */
	public void insert(String userId, String passwordHash) {
		jdbcTemplate.update("INSERT INTO MEMBER (USER_ID, PASSWORD) VALUES (?, ?)", userId, passwordHash);
	}

}

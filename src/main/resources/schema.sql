-- 회원 테이블 (SQL Developer 등에서 한 번 실행)
CREATE TABLE MEMBER (
    USER_ID   VARCHAR2(50)  PRIMARY KEY,
    PASSWORD  VARCHAR2(100) NOT NULL,   -- BCrypt 해시(60자)가 저장됨
    REG_DATE  DATE          DEFAULT SYSDATE NOT NULL
);

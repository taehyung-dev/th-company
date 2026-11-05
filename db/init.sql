CREATE DATABASE IF NOT EXISTS th_company
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 앱 서버와 DB 가 같은 서버에 있으므로 localhost 에서만 접속 허용
CREATE USER IF NOT EXISTS 'th_company'@'localhost' IDENTIFIED BY '변경할비밀번호';
GRANT ALL PRIVILEGES ON th_company.* TO 'th_company'@'localhost';
FLUSH PRIVILEGES;

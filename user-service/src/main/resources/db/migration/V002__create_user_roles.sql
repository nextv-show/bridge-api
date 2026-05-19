CREATE TABLE user_roles (
  user_id   BIGINT NOT NULL,
  role      ENUM('CONSUMER','OWNER','PROMOTER') NOT NULL,
  granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id)
);

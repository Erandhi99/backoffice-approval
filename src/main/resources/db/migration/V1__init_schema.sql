-- Initial schema for the back-office client approval workflow.

CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(150) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    role        VARCHAR(30)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE client_requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id           BIGINT       NOT NULL,
    name                VARCHAR(150) NOT NULL,
    nic                 VARCHAR(20)  NOT NULL,
    address             VARCHAR(300) NOT NULL,
    date_of_birth       DATE         NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    current_stage       VARCHAR(30)  NULL,
    rejection_stage     VARCHAR(30)  NULL,
    rejection_comment   VARCHAR(1000) NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_requests_client FOREIGN KEY (client_id) REFERENCES users (id),
    INDEX idx_requests_status (status),
    INDEX idx_requests_client (client_id)
) ENGINE=InnoDB;

CREATE TABLE approval_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id      BIGINT       NOT NULL,
    action          VARCHAR(30)  NOT NULL,
    stage           VARCHAR(30)  NULL,
    performed_by    BIGINT       NOT NULL,
    comment         VARCHAR(1000) NULL,
    timestamp       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_request FOREIGN KEY (request_id) REFERENCES client_requests (id),
    CONSTRAINT fk_history_user FOREIGN KEY (performed_by) REFERENCES users (id),
    INDEX idx_history_request (request_id)
) ENGINE=InnoDB;
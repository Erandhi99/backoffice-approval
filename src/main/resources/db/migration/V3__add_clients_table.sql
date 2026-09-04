-- The permanent, system-of-record client table. A row is inserted here ONLY when
-- a client_requests row reaches final (MANAGER-level) approval. Nothing else
-- ever writes to this table -- see ClientRequestService.approve().

CREATE TABLE clients (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    nic                 VARCHAR(20)  NOT NULL,
    address             VARCHAR(300) NOT NULL,
    date_of_birth       DATE         NOT NULL,
    source_request_id   BIGINT       NOT NULL,
    approved_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_clients_nic UNIQUE (nic),
    CONSTRAINT uk_clients_source_request UNIQUE (source_request_id),
    CONSTRAINT fk_clients_source_request FOREIGN KEY (source_request_id) REFERENCES client_requests (id)
) ENGINE=InnoDB;
-- Add fund management: 10 predefined funds, fund investments on requests,
-- and permanent client fund tracking.

-- 1. Funds master table
CREATE TABLE funds (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(80)  NOT NULL,
    url         VARCHAR(500) NOT NULL,
    CONSTRAINT uk_funds_slug UNIQUE (slug)
) ENGINE=InnoDB;

-- Seed the 10 Senfin funds
INSERT INTO funds (name, slug, url) VALUES
('Senfin Money Market Fund',    'senfin-money-market-fund',    'https://senfinassetmanagement.com/fund/senfin-money-market-fund/'),
('Senfin Dynamic Income Fund',  'senfin-dynamic-income-fund',  'https://senfinassetmanagement.com/fund/senfin-dynamic-income-fund/'),
('Senfin Growth Fund',          'senfin-growth-fund',          'https://senfinassetmanagement.com/fund/senfin-growth-fund/'),
('Senfin Shariah Income Fund',  'senfin-shariah-income-fund',  'https://senfinassetmanagement.com/fund/senfin-shariah-income-fund/'),
('Senfin Shariah Balanced Fund','senfin-shariah-balanced-fund','https://senfinassetmanagement.com/fund/senfin-shariah-balanced-fund/'),
('Senfin Dividend Fund',        'senfin-dividend-fund',        'https://senfinassetmanagement.com/fund/senfin-dividend-fund/'),
('Senfin Insurance Sector Fund','senfin-insurance-sector-fund','https://senfinassetmanagement.com/fund/senfin-insurance-sector-fund/'),
('Senfin Financial Services Fund','senfin-financial-services-fund','https://senfinassetmanagement.com/fund/senfin-financial-services-fund/'),
('Senfin Consumer Staples Fund', 'senfin-consumer-staples-fund','https://senfinassetmanagement.com/fund/senfin-consumer-staples-fund/'),
('Senfin Select Factor Fund',   'senfin-select-factor-fund',   'https://senfinassetmanagement.com/fund/senfin-select-factor-fund/');

-- 2. Client request fund investments (each request can invest in multiple funds)
CREATE TABLE client_request_funds (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id  BIGINT       NOT NULL,
    fund_id     BIGINT       NOT NULL,
    amount      DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_crf_request FOREIGN KEY (request_id) REFERENCES client_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_crf_fund    FOREIGN KEY (fund_id)    REFERENCES funds (id),
    INDEX idx_crf_request (request_id),
    INDEX idx_crf_fund (fund_id)
) ENGINE=InnoDB;

-- 3. Remove personal-info columns from client_requests (now come from User account)
ALTER TABLE client_requests
    DROP COLUMN name,
    DROP COLUMN nic,
    DROP COLUMN address,
    DROP COLUMN date_of_birth;

-- 4. Client fund investments (permanent record of what each approved client holds)
CREATE TABLE client_fund_investments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id           BIGINT       NOT NULL,
    fund_id             BIGINT       NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    source_request_id   BIGINT       NOT NULL,
    CONSTRAINT fk_cfi_client   FOREIGN KEY (client_id)         REFERENCES clients (id),
    CONSTRAINT fk_cfi_fund     FOREIGN KEY (fund_id)            REFERENCES funds (id),
    CONSTRAINT fk_cfi_request  FOREIGN KEY (source_request_id)  REFERENCES client_requests (id),
    INDEX idx_cfi_client (client_id),
    INDEX idx_cfi_fund (fund_id)
) ENGINE=InnoDB;

-- 5. Alter permanent clients table: add user_id FK, drop source_request_id unique
ALTER TABLE clients
    ADD COLUMN user_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_clients_user FOREIGN KEY (user_id) REFERENCES users (id),
    DROP INDEX uk_clients_source_request,
    DROP FOREIGN KEY fk_clients_source_request,
    DROP COLUMN source_request_id;

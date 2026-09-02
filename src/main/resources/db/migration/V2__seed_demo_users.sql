-- Demo accounts for local development / testing the workflow end-to-end.
-- Password for ALL accounts below is: password123
-- (BCrypt hash, generated once — never regenerate a different hash per row unless you
-- want different passwords; BCrypt salts are embedded in the hash itself.)

INSERT INTO users (username, password, full_name, email, role, enabled) VALUES
('client1',    '$2b$10$4OabYJP09qBFfOkhd2f/WeYLjFXMWHf6KmWNpFYXZl8zw7wcYj4ia', 'Nimal Perera',    'client1@example.com',    'CLIENT',            TRUE),
('entrymgr1',  '$2b$10$4OabYJP09qBFfOkhd2f/WeYLjFXMWHf6KmWNpFYXZl8zw7wcYj4ia', 'Kamal Silva',     'entrymgr1@example.com', 'ENTRY_MANAGER',     TRUE),
('asstmgr1',   '$2b$10$4OabYJP09qBFfOkhd2f/WeYLjFXMWHf6KmWNpFYXZl8zw7wcYj4ia', 'Sunil Fernando',  'asstmgr1@example.com',  'ASSISTANT_MANAGER', TRUE),
('manager1',   '$2b$10$4OabYJP09qBFfOkhd2f/WeYLjFXMWHf6KmWNpFYXZl8zw7wcYj4ia', 'Ranjith Bandara', 'manager1@example.com',  'MANAGER',           TRUE);
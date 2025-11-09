-- liquibase formatted sql

-- changeset finpulse:1
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    bank_client_id VARCHAR(100) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT true,
    is_verified BOOLEAN DEFAULT false,
    verification_token VARCHAR(255),
    verification_token_expiry TIMESTAMP,
    verification_status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- changeset finpulse:2
CREATE TABLE IF NOT EXISTS banks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- changeset finpulse:3
CREATE TABLE IF NOT EXISTS user_consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_client_id VARCHAR(100) NOT NULL,
    bank_id UUID NOT NULL,
    consent_id VARCHAR(255),
    request_id VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING',
    permissions TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- changeset finpulse:4
CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_consent_id UUID NOT NULL,
    external_account_id VARCHAR(100) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    account_sub_type VARCHAR(50),
    currency VARCHAR(3) DEFAULT 'RUB',
    balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    available_balance DECIMAL(15,2) DEFAULT 0,
    account_name VARCHAR(255),
    nickname VARCHAR(255),
    status VARCHAR(50) DEFAULT 'Enabled',
    opening_date DATE,
    is_active BOOLEAN DEFAULT true,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- changeset finpulse:5
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    user_id UUID NOT NULL,
    bank_client_id VARCHAR(100) NOT NULL,
    external_transaction_id VARCHAR(100) NOT NULL,
    booking_date TIMESTAMP NOT NULL,
    amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    absolute_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    is_expense BOOLEAN NOT NULL,
    transaction_information TEXT,
    credit_debit_indicator VARCHAR(10) NOT NULL,
    category VARCHAR(100)
);

-- changeset finpulse:6
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(7) DEFAULT '#666666',
    icon VARCHAR(50),
    parent_id UUID,
    is_system BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- changeset finpulse:7
CREATE TABLE IF NOT EXISTS budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    period VARCHAR(20) NOT NULL DEFAULT 'monthly',
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- changeset finpulse:8
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'system',
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT false,
    related_entity_type VARCHAR(50),
    related_entity_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- changeset finpulse:9
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);

-- changeset finpulse:10
CREATE INDEX IF NOT EXISTS idx_banks_code ON banks(code);
CREATE INDEX IF NOT EXISTS idx_banks_is_active ON banks(is_active);

-- changeset finpulse:11
CREATE INDEX IF NOT EXISTS idx_user_consents_bank_client_id ON user_consents(bank_client_id);
CREATE INDEX IF NOT EXISTS idx_user_consents_bank_id ON user_consents(bank_id);
CREATE INDEX IF NOT EXISTS idx_user_consents_consent_id ON user_consents(consent_id);
CREATE INDEX IF NOT EXISTS idx_user_consents_request_id ON user_consents(request_id);
CREATE INDEX IF NOT EXISTS idx_user_consents_status ON user_consents(status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_consents_client_bank_unique ON user_consents(bank_client_id, bank_id);

-- changeset finpulse:12
CREATE INDEX IF NOT EXISTS idx_accounts_user_consent_id ON accounts(user_consent_id);
CREATE INDEX IF NOT EXISTS idx_accounts_external_account_id ON accounts(external_account_id);
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts(account_number);
CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_user_consent_external_unique ON accounts(user_consent_id, external_account_id);

-- changeset finpulse:13
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_external_transaction_id ON transactions(external_transaction_id);
CREATE INDEX IF NOT EXISTS idx_transactions_booking_date ON transactions(booking_date);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(category);
CREATE INDEX IF NOT EXISTS idx_transactions_is_expense ON transactions(is_expense);
CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_account_external_unique ON transactions(account_id, external_transaction_id);
CREATE INDEX IF NOT EXISTS idx_transactions_bank_client_id ON transactions(bank_client_id);
CREATE INDEX IF NOT EXISTS idx_transactions_bank_client_booking_date ON transactions(bank_client_id, booking_date);

-- changeset finpulse:14
CREATE INDEX IF NOT EXISTS idx_categories_user_id ON categories(user_id);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_is_system ON categories(is_system);

-- changeset finpulse:15
CREATE INDEX IF NOT EXISTS idx_budgets_user_id ON budgets(user_id);
CREATE INDEX IF NOT EXISTS idx_budgets_category_id ON budgets(category_id);
CREATE INDEX IF NOT EXISTS idx_budgets_period ON budgets(period);

-- changeset finpulse:16
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications(is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at);

-- changeset finpulse:17
ALTER TABLE user_consents ADD FOREIGN KEY (bank_client_id) REFERENCES users(bank_client_id);
ALTER TABLE user_consents ADD FOREIGN KEY (bank_id) REFERENCES banks(id);
ALTER TABLE accounts ADD FOREIGN KEY (user_consent_id) REFERENCES user_consents(id);
ALTER TABLE transactions ADD FOREIGN KEY (account_id) REFERENCES accounts(id);
ALTER TABLE transactions ADD FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE transactions ADD FOREIGN KEY (bank_client_id) REFERENCES users(bank_client_id);
ALTER TABLE categories ADD FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE categories ADD FOREIGN KEY (parent_id) REFERENCES categories(id);
ALTER TABLE categories ADD CONSTRAINT categories_name_unique UNIQUE (name);
ALTER TABLE budgets ADD FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE budgets ADD FOREIGN KEY (category_id) REFERENCES categories(id);
ALTER TABLE notifications ADD FOREIGN KEY (user_id) REFERENCES users(id);

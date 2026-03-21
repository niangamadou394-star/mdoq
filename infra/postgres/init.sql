-- ============================================================
-- Medoq Platform – Initial Schema
-- PostgreSQL 16
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "cube";          -- required by earthdistance
CREATE EXTENSION IF NOT EXISTS "earthdistance"; -- ll_to_earth / earth_distance / earth_box

-- ============================================================
-- ENUM TYPES
-- ============================================================

CREATE TYPE user_role AS ENUM ('ADMIN', 'PHARMACY_OWNER', 'PHARMACY_STAFF', 'CUSTOMER');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION');
CREATE TYPE pharmacy_status AS ENUM ('ACTIVE', 'INACTIVE', 'PENDING_APPROVAL', 'SUSPENDED');
CREATE TYPE reservation_status AS ENUM ('PENDING', 'CONFIRMED', 'PAID', 'READY', 'COMPLETED', 'CANCELLED', 'EXPIRED');
CREATE TYPE payment_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');
CREATE TYPE payment_method AS ENUM ('ORANGE_MONEY', 'WAVE', 'FREE_MONEY', 'CARD', 'CASH');
CREATE TYPE notification_type AS ENUM ('RESERVATION_UPDATE', 'STOCK_ALERT', 'PAYMENT', 'SYSTEM', 'PROMOTION');

-- ============================================================
-- 1. USERS
-- ============================================================

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) UNIQUE,
    phone         VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    role          user_role NOT NULL DEFAULT 'CUSTOMER',
    status        user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    avatar_url    VARCHAR(500),
    refresh_token TEXT,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone  ON users(phone);
CREATE INDEX idx_users_email  ON users(email);
CREATE INDEX idx_users_role   ON users(role);
CREATE INDEX idx_users_status ON users(status);

-- ============================================================
-- 2. PHARMACIES
-- ============================================================

CREATE TABLE pharmacies (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name             VARCHAR(255) NOT NULL,
    license_number   VARCHAR(100) UNIQUE NOT NULL,
    phone            VARCHAR(20) NOT NULL,
    email            VARCHAR(255),
    address          TEXT NOT NULL,
    city             VARCHAR(100) NOT NULL,
    region           VARCHAR(100) NOT NULL,
    latitude         DECIMAL(10, 8),
    longitude        DECIMAL(11, 8),
    opening_hours    JSONB,          -- {"mon": "08:00-20:00", "sun": "closed", ...}
    is_24h           BOOLEAN NOT NULL DEFAULT FALSE,
    logo_url         VARCHAR(500),
    status           pharmacy_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    rating           DECIMAL(3, 2) DEFAULT 0.00,
    review_count     INTEGER NOT NULL DEFAULT 0,
    owner_id         UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pharmacies_owner    ON pharmacies(owner_id);
CREATE INDEX idx_pharmacies_city     ON pharmacies(city);
CREATE INDEX idx_pharmacies_region   ON pharmacies(region);
CREATE INDEX idx_pharmacies_status   ON pharmacies(status);
CREATE INDEX idx_pharmacies_location ON pharmacies USING GIST (
    ll_to_earth(latitude::float8, longitude::float8)
) WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- ============================================================
-- 3. PHARMACY_USERS  (staff members per pharmacy)
-- ============================================================

CREATE TABLE pharmacy_users (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pharmacy_id UUID NOT NULL REFERENCES pharmacies(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        user_role NOT NULL DEFAULT 'PHARMACY_STAFF',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (pharmacy_id, user_id)
);

CREATE INDEX idx_pharmacy_users_pharmacy ON pharmacy_users(pharmacy_id);
CREATE INDEX idx_pharmacy_users_user     ON pharmacy_users(user_id);

-- ============================================================
-- 4. MEDICATIONS
-- ============================================================

CREATE TABLE medications (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    generic_name    VARCHAR(255),
    brand_name      VARCHAR(255),
    dci             VARCHAR(255),   -- Dénomination Commune Internationale
    category        VARCHAR(100),
    dosage_form     VARCHAR(100),   -- tablet, syrup, injection…
    strength        VARCHAR(100),   -- 500mg, 250mg/5ml…
    description     TEXT,
    contraindications TEXT,
    requires_prescription BOOLEAN NOT NULL DEFAULT FALSE,
    image_url       VARCHAR(500),
    barcode         VARCHAR(100) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_medications_name     ON medications(name);
CREATE INDEX idx_medications_category ON medications(category);
CREATE INDEX idx_medications_barcode  ON medications(barcode);
CREATE INDEX idx_medications_fts      ON medications USING GIN (
    to_tsvector('french', name || ' ' || COALESCE(generic_name, '') || ' ' || COALESCE(dci, ''))
);

-- ============================================================
-- 5. PHARMACY_STOCK
-- ============================================================

CREATE TABLE pharmacy_stock (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pharmacy_id     UUID NOT NULL REFERENCES pharmacies(id) ON DELETE CASCADE,
    medication_id   UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    quantity        INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    unit_price      DECIMAL(10, 2) NOT NULL CHECK (unit_price >= 0),
    expiry_date     DATE,
    reorder_level   INTEGER NOT NULL DEFAULT 10,
    is_available    BOOLEAN NOT NULL DEFAULT TRUE,
    last_restocked  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (pharmacy_id, medication_id)
);

CREATE INDEX idx_stock_pharmacy   ON pharmacy_stock(pharmacy_id);
CREATE INDEX idx_stock_medication ON pharmacy_stock(medication_id);
CREATE INDEX idx_stock_available  ON pharmacy_stock(pharmacy_id, is_available) WHERE is_available = TRUE;

-- ============================================================
-- 6. RESERVATIONS
-- ============================================================

CREATE TABLE reservations (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference         VARCHAR(20) UNIQUE NOT NULL,
    customer_id       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    pharmacy_id       UUID NOT NULL REFERENCES pharmacies(id) ON DELETE RESTRICT,
    status            reservation_status NOT NULL DEFAULT 'PENDING',
    total_amount      DECIMAL(10, 2) NOT NULL CHECK (total_amount >= 0),
    pickup_date       TIMESTAMPTZ,
    notes             TEXT,
    expires_at        TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '2 hours'),
    confirmed_at      TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancellation_reason   TEXT,
    expiry_warning_sent   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reservation_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reservation_id  UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    medication_id   UUID NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    unit_price      DECIMAL(10, 2) NOT NULL CHECK (unit_price >= 0),
    subtotal        DECIMAL(10, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED
);

CREATE INDEX idx_reservations_customer        ON reservations(customer_id);
CREATE INDEX idx_reservations_pharmacy        ON reservations(pharmacy_id);
CREATE INDEX idx_reservations_status          ON reservations(status);
CREATE INDEX idx_reservations_reference       ON reservations(reference);
-- Partial index for the expiry cron job — only scans PENDING rows
CREATE INDEX idx_reservations_pending_expires ON reservations(expires_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_reservation_items_res  ON reservation_items(reservation_id);

-- ============================================================
-- 7. PAYMENTS
-- ============================================================

CREATE TABLE payments (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reservation_id   UUID NOT NULL REFERENCES reservations(id) ON DELETE RESTRICT,
    customer_id      UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount           DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    method           payment_method NOT NULL,
    status           payment_status NOT NULL DEFAULT 'PENDING',
    transaction_ref  VARCHAR(255) UNIQUE,
    provider_ref     VARCHAR(255),   -- reference from Orange Money / Wave / etc.
    paid_at          TIMESTAMPTZ,
    failed_at        TIMESTAMPTZ,
    failure_reason   TEXT,
    commission_rate   DECIMAL(5, 4),          -- e.g. 0.0150 = 1.5 %
    commission_amount DECIMAL(10, 2),          -- amount * commission_rate
    net_amount        DECIMAL(10, 2),          -- amount - commission_amount (pharmacy receives)
    provider_session  VARCHAR(500),            -- Wave checkout session / Orange payment token
    metadata          JSONB,                   -- provider-specific encrypted payload
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_reservation   ON payments(reservation_id);
CREATE INDEX idx_payments_customer      ON payments(customer_id);
CREATE INDEX idx_payments_status        ON payments(status);
CREATE INDEX idx_payments_tx_ref        ON payments(transaction_ref);
CREATE INDEX idx_payments_provider_sess ON payments(provider_session);

-- ============================================================
-- 10. AUDIT_LOGS
-- ============================================================

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id   UUID,
    ip_address    VARCHAR(45),
    status        VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS | FAILURE
    old_value     JSONB,
    new_value     JSONB,
    metadata      JSONB
);

CREATE INDEX idx_audit_logs_user      ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_resource  ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_action    ON audit_logs(action);

-- ============================================================
-- 8. NOTIFICATIONS
-- ============================================================

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        notification_type NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    data        JSONB,           -- arbitrary payload (reservation_id, etc.)
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMPTZ,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user    ON notifications(user_id);
CREATE INDEX idx_notifications_unread  ON notifications(user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_type    ON notifications(type);

-- ============================================================
-- 9. REVIEWS
-- ============================================================

CREATE TABLE reviews (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pharmacy_id UUID NOT NULL REFERENCES pharmacies(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,   -- purchased before reviewing
    is_visible  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (pharmacy_id, customer_id)
);

CREATE INDEX idx_reviews_pharmacy ON reviews(pharmacy_id);
CREATE INDEX idx_reviews_customer ON reviews(customer_id);
CREATE INDEX idx_reviews_rating   ON reviews(pharmacy_id, rating);

-- ============================================================
-- AUTO-UPDATE updated_at trigger
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users', 'pharmacies', 'medications',
        'pharmacy_stock', 'reservations', 'payments', 'reviews'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at
             BEFORE UPDATE ON %s
             FOR EACH ROW EXECUTE FUNCTION update_updated_at()',
            t, t
        );
    END LOOP;
END;
$$;

-- ============================================================
-- AUTO-UPDATE pharmacy rating on review insert/update/delete
-- ============================================================

CREATE OR REPLACE FUNCTION refresh_pharmacy_rating()
RETURNS TRIGGER AS $$
DECLARE
    target_pharmacy_id UUID;
BEGIN
    target_pharmacy_id := COALESCE(NEW.pharmacy_id, OLD.pharmacy_id);
    UPDATE pharmacies
    SET rating       = COALESCE((SELECT AVG(rating) FROM reviews WHERE pharmacy_id = target_pharmacy_id AND is_visible = TRUE), 0),
        review_count = (SELECT COUNT(*) FROM reviews WHERE pharmacy_id = target_pharmacy_id AND is_visible = TRUE)
    WHERE id = target_pharmacy_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reviews_refresh_rating
AFTER INSERT OR UPDATE OR DELETE ON reviews
FOR EACH ROW EXECUTE FUNCTION refresh_pharmacy_rating();

-- ============================================================
-- RESERVATION REFERENCE GENERATOR
-- ============================================================

CREATE SEQUENCE reservation_seq START 1000;

CREATE OR REPLACE FUNCTION generate_reservation_reference()
RETURNS TRIGGER AS $$
BEGIN
    NEW.reference := 'MQ-' || TO_CHAR(NOW(), 'YYMMDD') || '-' || LPAD(NEXTVAL('reservation_seq')::TEXT, 5, '0');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reservation_reference
BEFORE INSERT ON reservations
FOR EACH ROW WHEN (NEW.reference IS NULL OR NEW.reference = '')
EXECUTE FUNCTION generate_reservation_reference();


-- ============================================================
-- DEVICE TOKENS (FCM push notifications)
-- ============================================================

CREATE TYPE device_platform AS ENUM ('ANDROID', 'IOS');

CREATE TABLE device_tokens (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token         VARCHAR(512) NOT NULL,
    platform      device_platform NOT NULL,
    app_version   VARCHAR(20),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at  TIMESTAMPTZ,

    CONSTRAINT uq_device_token UNIQUE (token)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(80)  NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    module      VARCHAR(50)  NOT NULL
);

CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,
    email                 VARCHAR(150) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(150) NOT NULL,
    phone                 VARCHAR(30),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id),
    role_id BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE login_attempts (
    username     VARCHAR(50) PRIMARY KEY,
    failures     INTEGER     NOT NULL,
    locked_until TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE dosage_forms (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(50) NOT NULL UNIQUE,
    active BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE units_of_measure (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(30) NOT NULL UNIQUE,
    active BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE locations (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT REFERENCES locations (id),
    location_type VARCHAR(20)  NOT NULL,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(150) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_location_type CHECK (location_type IN ('STORE', 'SHELF', 'RACK', 'POSITION'))
);
CREATE INDEX idx_locations_parent ON locations (parent_id);

CREATE TABLE medicines (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200)   NOT NULL,
    generic_name    VARCHAR(200)   NOT NULL,
    brand_name      VARCHAR(200),
    category_id     BIGINT         REFERENCES categories (id),
    dosage_form     VARCHAR(50)    NOT NULL,
    strength        VARCHAR(50)    NOT NULL,
    unit            VARCHAR(30)    NOT NULL,
    manufacturer    VARCHAR(150),
    barcode         VARCHAR(80)    UNIQUE,
    reorder_level   INTEGER        NOT NULL DEFAULT 0,
    purchase_price  NUMERIC(14, 2) NOT NULL,
    selling_price   NUMERIC(14, 2) NOT NULL,
    location_id     BIGINT         REFERENCES locations (id),
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_medicine_reorder CHECK (reorder_level >= 0),
    CONSTRAINT chk_medicine_purchase_price CHECK (purchase_price >= 0),
    CONSTRAINT chk_medicine_selling_price CHECK (selling_price >= 0)
);
CREATE INDEX idx_medicines_name ON medicines (LOWER(name));
CREATE INDEX idx_medicines_generic ON medicines (LOWER(generic_name));
CREATE INDEX idx_medicines_category ON medicines (category_id);
CREATE INDEX idx_medicines_location ON medicines (location_id);
CREATE INDEX idx_medicines_active ON medicines (active);

CREATE TABLE suppliers (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    contact_person VARCHAR(150),
    phone          VARCHAR(30),
    email          VARCHAR(150),
    address        VARCHAR(500),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_suppliers_name ON suppliers (LOWER(name));

CREATE TABLE purchases (
    id                   BIGSERIAL PRIMARY KEY,
    reference            VARCHAR(40)    NOT NULL UNIQUE,
    supplier_id          BIGINT         NOT NULL REFERENCES suppliers (id),
    purchase_date        DATE           NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    payment_status       VARCHAR(20)    NOT NULL,
    total_amount         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    amount_paid          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes                VARCHAR(1000),
    created_by           BIGINT         NOT NULL REFERENCES users (id),
    confirmed_by         BIGINT         REFERENCES users (id),
    confirmed_at         TIMESTAMPTZ,
    cancelled_by         BIGINT         REFERENCES users (id),
    cancelled_at         TIMESTAMPTZ,
    cancellation_reason  VARCHAR(500),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_purchase_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT chk_purchase_payment_status CHECK (payment_status IN ('UNPAID', 'PARTIAL', 'PAID')),
    CONSTRAINT chk_purchase_amounts CHECK (total_amount >= 0 AND amount_paid >= 0 AND amount_paid <= total_amount)
);
CREATE INDEX idx_purchases_supplier ON purchases (supplier_id);
CREATE INDEX idx_purchases_date ON purchases (purchase_date);
CREATE INDEX idx_purchases_status ON purchases (status);

CREATE TABLE purchase_items (
    id                  BIGSERIAL PRIMARY KEY,
    purchase_id         BIGINT         NOT NULL REFERENCES purchases (id),
    medicine_id         BIGINT         NOT NULL REFERENCES medicines (id),
    quantity            INTEGER        NOT NULL,
    purchase_price      NUMERIC(14, 2) NOT NULL,
    selling_price       NUMERIC(14, 2) NOT NULL,
    batch_number        VARCHAR(80)    NOT NULL,
    manufacturing_date  DATE,
    expiry_date         DATE           NOT NULL,
    location_id         BIGINT         REFERENCES locations (id),
    line_total          NUMERIC(14, 2) NOT NULL,
    CONSTRAINT chk_purchase_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_purchase_item_prices CHECK (purchase_price >= 0 AND selling_price >= 0 AND line_total >= 0),
    CONSTRAINT uq_purchase_item_batch UNIQUE (purchase_id, medicine_id, batch_number)
);
CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id);
CREATE INDEX idx_purchase_items_medicine ON purchase_items (medicine_id);

CREATE TABLE batches (
    id                  BIGSERIAL PRIMARY KEY,
    medicine_id         BIGINT         NOT NULL REFERENCES medicines (id),
    batch_number        VARCHAR(80)    NOT NULL,
    manufacturing_date  DATE,
    expiry_date         DATE           NOT NULL,
    quantity_received   INTEGER        NOT NULL,
    quantity_on_hand    INTEGER        NOT NULL,
    purchase_price      NUMERIC(14, 2) NOT NULL,
    selling_price       NUMERIC(14, 2) NOT NULL,
    supplier_id         BIGINT         REFERENCES suppliers (id),
    location_id         BIGINT         REFERENCES locations (id),
    purchase_item_id    BIGINT         UNIQUE REFERENCES purchase_items (id),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_batch_qty CHECK (quantity_received >= 0 AND quantity_on_hand >= 0),
    CONSTRAINT chk_batch_prices CHECK (purchase_price >= 0 AND selling_price >= 0),
    CONSTRAINT uq_batch_medicine_number UNIQUE (medicine_id, batch_number)
);
CREATE INDEX idx_batches_medicine ON batches (medicine_id);
CREATE INDEX idx_batches_expiry ON batches (expiry_date);
CREATE INDEX idx_batches_supplier ON batches (supplier_id);

CREATE TABLE stock_movements (
    id             BIGSERIAL PRIMARY KEY,
    medicine_id    BIGINT      NOT NULL REFERENCES medicines (id),
    batch_id       BIGINT      NOT NULL REFERENCES batches (id),
    movement_type  VARCHAR(30) NOT NULL,
    quantity       INTEGER     NOT NULL,
    direction      VARCHAR(3)  NOT NULL,
    balance_after  INTEGER     NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    reference_id   BIGINT,
    reason         VARCHAR(500),
    performed_by   BIGINT      NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_movement_qty CHECK (quantity > 0 AND balance_after >= 0),
    CONSTRAINT chk_movement_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_movement_type CHECK (movement_type IN (
        'RECEIVED', 'SOLD', 'DISPENSED', 'RETURNED', 'ADJUSTED', 'DAMAGED', 'EXPIRED',
        'SALE_REVERSAL', 'PURCHASE_REVERSAL'
    ))
);
CREATE INDEX idx_stock_movements_medicine ON stock_movements (medicine_id, created_at DESC);
CREATE INDEX idx_stock_movements_batch ON stock_movements (batch_id);
CREATE INDEX idx_stock_movements_created ON stock_movements (created_at DESC);
CREATE INDEX idx_stock_movements_type ON stock_movements (movement_type);

CREATE TABLE customers (
    id            BIGSERIAL PRIMARY KEY,
    reference     VARCHAR(40)  NOT NULL UNIQUE,
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(30),
    email         VARCHAR(150),
    address       VARCHAR(500),
    date_of_birth DATE,
    notes         VARCHAR(1000),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_customers_name ON customers (LOWER(full_name));
CREATE INDEX idx_customers_phone ON customers (phone);

CREATE TABLE prescriptions (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(40) NOT NULL UNIQUE,
    customer_id         BIGINT      NOT NULL REFERENCES customers (id),
    prescription_date   DATE        NOT NULL,
    prescriber_name     VARCHAR(150),
    prescriber_license  VARCHAR(80),
    status              VARCHAR(30) NOT NULL,
    notes               VARCHAR(1000),
    created_by          BIGINT      NOT NULL REFERENCES users (id),
    reviewed_by         BIGINT      REFERENCES users (id),
    reviewed_at         TIMESTAMPTZ,
    cancelled_by        BIGINT      REFERENCES users (id),
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_prescription_status CHECK (status IN (
        'PENDING', 'REVIEWED', 'PARTIALLY_DISPENSED', 'FULLY_DISPENSED', 'CANCELLED'
    ))
);
CREATE INDEX idx_prescriptions_customer ON prescriptions (customer_id);
CREATE INDEX idx_prescriptions_status ON prescriptions (status);
CREATE INDEX idx_prescriptions_date ON prescriptions (prescription_date);

CREATE TABLE prescription_items (
    id                  BIGSERIAL PRIMARY KEY,
    prescription_id     BIGINT       NOT NULL REFERENCES prescriptions (id),
    medicine_id         BIGINT       NOT NULL REFERENCES medicines (id),
    dosage              VARCHAR(80)  NOT NULL,
    frequency           VARCHAR(80)  NOT NULL,
    duration            VARCHAR(80)  NOT NULL,
    quantity            INTEGER      NOT NULL,
    instructions        VARCHAR(500),
    quantity_dispensed  INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_rx_item_qty CHECK (quantity > 0 AND quantity_dispensed >= 0 AND quantity_dispensed <= quantity)
);
CREATE INDEX idx_prescription_items_rx ON prescription_items (prescription_id);

CREATE TABLE dispensings (
    id              BIGSERIAL PRIMARY KEY,
    reference       VARCHAR(40) NOT NULL UNIQUE,
    prescription_id BIGINT      NOT NULL REFERENCES prescriptions (id),
    dispensed_by    BIGINT      NOT NULL REFERENCES users (id),
    dispensed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes           VARCHAR(1000),
    expired_authorized BOOLEAN  NOT NULL DEFAULT FALSE,
    expired_reason  VARCHAR(500)
);
CREATE INDEX idx_dispensings_rx ON dispensings (prescription_id);
CREATE INDEX idx_dispensings_at ON dispensings (dispensed_at DESC);

CREATE TABLE dispensing_items (
    id                   BIGSERIAL PRIMARY KEY,
    dispensing_id        BIGINT  NOT NULL REFERENCES dispensings (id),
    prescription_item_id BIGINT  NOT NULL REFERENCES prescription_items (id),
    batch_id             BIGINT  NOT NULL REFERENCES batches (id),
    quantity             INTEGER NOT NULL,
    CONSTRAINT chk_dispensing_item_qty CHECK (quantity > 0)
);
CREATE INDEX idx_dispensing_items_dispensing ON dispensing_items (dispensing_id);

CREATE TABLE sales (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(40)    NOT NULL UNIQUE,
    customer_id         BIGINT         REFERENCES customers (id),
    status              VARCHAR(20)    NOT NULL,
    subtotal            NUMERIC(14, 2) NOT NULL,
    discount_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(14, 2) NOT NULL,
    notes               VARCHAR(1000),
    cashier_id          BIGINT         NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    cancelled_by        BIGINT         REFERENCES users (id),
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    expired_authorized  BOOLEAN        NOT NULL DEFAULT FALSE,
    expired_reason      VARCHAR(500),
    CONSTRAINT chk_sale_status CHECK (status IN ('COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_sale_amounts CHECK (subtotal >= 0 AND discount_amount >= 0 AND total_amount >= 0)
);
CREATE INDEX idx_sales_created ON sales (created_at DESC);
CREATE INDEX idx_sales_cashier ON sales (cashier_id);
CREATE INDEX idx_sales_status ON sales (status);
CREATE INDEX idx_sales_customer ON sales (customer_id);

CREATE TABLE sale_items (
    id               BIGSERIAL PRIMARY KEY,
    sale_id          BIGINT         NOT NULL REFERENCES sales (id),
    medicine_id      BIGINT         NOT NULL REFERENCES medicines (id),
    batch_id         BIGINT         NOT NULL REFERENCES batches (id),
    quantity         INTEGER        NOT NULL,
    unit_price       NUMERIC(14, 2) NOT NULL,
    discount_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    line_total       NUMERIC(14, 2) NOT NULL,
    quantity_returned INTEGER       NOT NULL DEFAULT 0,
    refunded_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    CONSTRAINT chk_sale_item_qty CHECK (quantity > 0 AND quantity_returned >= 0 AND quantity_returned <= quantity),
    CONSTRAINT chk_sale_item_money CHECK (unit_price >= 0 AND discount_amount >= 0 AND line_total >= 0 AND refunded_amount >= 0)
);
CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);
CREATE INDEX idx_sale_items_medicine ON sale_items (medicine_id);

CREATE TABLE payment_methods (
    id     BIGSERIAL PRIMARY KEY,
    code   VARCHAR(30) NOT NULL UNIQUE,
    name   VARCHAR(80) NOT NULL,
    active BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE payments (
    id                 BIGSERIAL PRIMARY KEY,
    sale_id            BIGINT         NOT NULL REFERENCES sales (id),
    payment_method_id  BIGINT         NOT NULL REFERENCES payment_methods (id),
    amount             NUMERIC(14, 2) NOT NULL,
    tendered_amount    NUMERIC(14, 2),
    change_amount      NUMERIC(14, 2),
    reference          VARCHAR(100),
    paid_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    received_by        BIGINT         NOT NULL REFERENCES users (id),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);
CREATE INDEX idx_payments_sale ON payments (sale_id);
CREATE INDEX idx_payments_method ON payments (payment_method_id);
CREATE INDEX idx_payments_paid_at ON payments (paid_at);

CREATE TABLE sale_returns (
    id                BIGSERIAL PRIMARY KEY,
    reference         VARCHAR(40)    NOT NULL UNIQUE,
    sale_id           BIGINT         NOT NULL REFERENCES sales (id),
    status            VARCHAR(20)    NOT NULL,
    reason            VARCHAR(500)   NOT NULL,
    refund_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    refund_method_id  BIGINT         REFERENCES payment_methods (id),
    refund_reference  VARCHAR(100),
    created_by        BIGINT         NOT NULL REFERENCES users (id),
    approved_by       BIGINT         REFERENCES users (id),
    approved_at       TIMESTAMPTZ,
    rejected_by       BIGINT         REFERENCES users (id),
    rejected_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_return_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_return_amount CHECK (refund_amount >= 0)
);
CREATE INDEX idx_returns_sale ON sale_returns (sale_id);
CREATE INDEX idx_returns_status ON sale_returns (status);
CREATE INDEX idx_returns_created ON sale_returns (created_at DESC);

CREATE TABLE return_items (
    id            BIGSERIAL PRIMARY KEY,
    return_id     BIGINT         NOT NULL REFERENCES sale_returns (id),
    sale_item_id  BIGINT         NOT NULL REFERENCES sale_items (id),
    batch_id      BIGINT         NOT NULL REFERENCES batches (id),
    quantity      INTEGER        NOT NULL,
    refund_amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT chk_return_item_qty CHECK (quantity > 0 AND refund_amount >= 0)
);
CREATE INDEX idx_return_items_return ON return_items (return_id);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users (id),
    username    VARCHAR(50),
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(50),
    details     JSONB,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_user ON audit_logs (user_id);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);

CREATE TABLE system_settings (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(80)  NOT NULL UNIQUE,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by    BIGINT       REFERENCES users (id)
);

CREATE TABLE document_sequences (
    seq_key    VARCHAR(40) PRIMARY KEY,
    last_value BIGINT NOT NULL
);

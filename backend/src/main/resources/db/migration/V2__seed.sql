INSERT INTO permissions (code, description, module) VALUES
    ('DASHBOARD_VIEW', 'View the operational dashboard', 'DASHBOARD'),
    ('USER_VIEW', 'View user accounts', 'USERS'),
    ('USER_MANAGE', 'Create, edit, activate and reset user accounts', 'USERS'),
    ('ROLE_MANAGE', 'View roles and update role permissions', 'USERS'),
    ('SETTINGS_MANAGE', 'Manage system settings and reference lists', 'SETTINGS'),
    ('AUDIT_VIEW', 'View the activity history', 'AUDIT'),
    ('MEDICINE_VIEW', 'View medicines and categories', 'MEDICINES'),
    ('MEDICINE_MANAGE', 'Create and edit medicines and categories', 'MEDICINES'),
    ('LOCATION_VIEW', 'View shelves, racks and storage locations', 'LOCATIONS'),
    ('LOCATION_MANAGE', 'Create and edit storage locations', 'LOCATIONS'),
    ('STOCK_VIEW', 'View stock, batches and stock movements', 'STOCK'),
    ('STOCK_ADJUST', 'Adjust stock and write off damaged or expired stock', 'STOCK'),
    ('SUPPLIER_VIEW', 'View suppliers', 'SUPPLIERS'),
    ('SUPPLIER_MANAGE', 'Create and edit suppliers', 'SUPPLIERS'),
    ('PURCHASE_VIEW', 'View purchases', 'PURCHASES'),
    ('PURCHASE_MANAGE', 'Create, confirm and cancel purchases', 'PURCHASES'),
    ('PRESCRIPTION_VIEW', 'View prescriptions', 'PRESCRIPTIONS'),
    ('PRESCRIPTION_MANAGE', 'Create and edit pending prescriptions', 'PRESCRIPTIONS'),
    ('PRESCRIPTION_REVIEW', 'Review prescriptions', 'PRESCRIPTIONS'),
    ('DISPENSE', 'Dispense reviewed prescriptions', 'DISPENSING'),
    ('DISPENSE_EXPIRED', 'Authorize dispensing of expired stock', 'DISPENSING'),
    ('SALE_VIEW', 'View sales', 'SALES'),
    ('SALE_CREATE', 'Complete sales at the point of sale', 'SALES'),
    ('SALE_CANCEL', 'Cancel a completed sale', 'SALES'),
    ('SALE_EXPIRED', 'Authorize sale of expired stock', 'SALES'),
    ('DISCOUNT_APPLY', 'Apply a discount on a sale', 'SALES'),
    ('CUSTOMER_VIEW', 'View customers and patients', 'CUSTOMERS'),
    ('CUSTOMER_MANAGE', 'Create and edit customers and patients', 'CUSTOMERS'),
    ('RETURN_VIEW', 'View sale returns', 'RETURNS'),
    ('RETURN_CREATE', 'Create a sale return', 'RETURNS'),
    ('RETURN_APPROVE', 'Approve or reject a sale return', 'RETURNS'),
    ('REPORT_VIEW', 'View and export reports', 'REPORTS'),
    ('ALERT_VIEW', 'View stock and prescription alerts', 'ALERTS');

INSERT INTO roles (name, description) VALUES
    ('ADMINISTRATOR', 'Full system administration'),
    ('PHARMACY_MANAGER', 'Monitor sales, purchases, stock, reports and activity'),
    ('PHARMACIST', 'Review prescriptions and dispense medicines'),
    ('CASHIER', 'Process sales, payments and authorized returns'),
    ('STOREKEEPER', 'Receive stock, manage locations, batches and adjustments');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW', 'AUDIT_VIEW', 'MEDICINE_VIEW', 'LOCATION_VIEW', 'STOCK_VIEW',
    'SUPPLIER_VIEW', 'PURCHASE_VIEW', 'PRESCRIPTION_VIEW', 'SALE_VIEW', 'SALE_CANCEL',
    'CUSTOMER_VIEW', 'RETURN_VIEW', 'RETURN_APPROVE', 'REPORT_VIEW', 'ALERT_VIEW',
    'DISCOUNT_APPLY'
)
WHERE r.name = 'PHARMACY_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW', 'MEDICINE_VIEW', 'LOCATION_VIEW', 'STOCK_VIEW',
    'PRESCRIPTION_VIEW', 'PRESCRIPTION_MANAGE', 'PRESCRIPTION_REVIEW', 'DISPENSE',
    'CUSTOMER_VIEW', 'CUSTOMER_MANAGE', 'ALERT_VIEW'
)
WHERE r.name = 'PHARMACIST';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW', 'MEDICINE_VIEW', 'SALE_VIEW', 'SALE_CREATE',
    'DISCOUNT_APPLY', 'CUSTOMER_VIEW', 'CUSTOMER_MANAGE', 'RETURN_VIEW',
    'RETURN_CREATE', 'RETURN_APPROVE', 'ALERT_VIEW'
)
WHERE r.name = 'CASHIER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW', 'MEDICINE_VIEW', 'LOCATION_VIEW', 'LOCATION_MANAGE',
    'STOCK_VIEW', 'STOCK_ADJUST', 'SUPPLIER_VIEW', 'PURCHASE_VIEW', 'PURCHASE_MANAGE',
    'ALERT_VIEW'
)
WHERE r.name = 'STOREKEEPER';

INSERT INTO payment_methods (code, name, active) VALUES
    ('CASH', 'Cash', TRUE),
    ('MOBILE_MONEY', 'Mobile Money', TRUE),
    ('BANK_CARD', 'Bank / Card', TRUE),
    ('OTHER', 'Other', TRUE);

INSERT INTO dosage_forms (name) VALUES
    ('Tablet'), ('Capsule'), ('Syrup'), ('Suspension'), ('Injection'),
    ('Cream'), ('Ointment'), ('Drops'), ('Inhaler'), ('Suppository'),
    ('Powder'), ('Solution');

INSERT INTO units_of_measure (name) VALUES
    ('Tablet'), ('Capsule'), ('Bottle'), ('Vial'), ('Tube'),
    ('Piece'), ('ml'), ('Pack'), ('Ampoule'), ('Sachet');

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
    ('pharmacy.name', 'Pharmacy', 'Name shown in the header and printed on receipts'),
    ('pharmacy.address', '', 'Address printed on receipts'),
    ('pharmacy.phone', '', 'Phone number printed on receipts'),
    ('pharmacy.currency', 'USD', 'ISO 4217 currency code used for display'),
    ('pharmacy.timezone', 'UTC', 'IANA timezone used for reports and the business day'),
    ('inventory.expiry_warning_days', '90', 'Days before expiry that raise a near-expiry alert'),
    ('inventory.allow_authorized_expired_use', 'true', 'When true, users with expired-stock permission may sell or dispense expired stock'),
    ('sales.max_discount_percent', '10', 'Maximum total discount percentage on a sale'),
    ('security.password_min_length', '8', 'Minimum password length'),
    ('receipt.footer', 'Thank you', 'Footer text printed on receipts');

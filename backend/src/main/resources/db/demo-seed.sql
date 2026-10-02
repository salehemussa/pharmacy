-- Sample pharmacy data for local use. Safe to run once; it stops if medicines already exist.
-- Staff passwords: pharmacist / Pharmacist1, cashier / Cashier123, storekeeper / Store12345

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    admin_id BIGINT;
    day DATE := CURRENT_DATE;
    key TEXT := to_char(CURRENT_DATE, 'YYYYMMDD');
    past_key TEXT := to_char(CURRENT_DATE - 5, 'YYYYMMDD');
    cat_abx BIGINT;
    cat_pain BIGINT;
    cat_chronic BIGINT;
    cat_resp BIGINT;
    cat_vit BIGINT;
    cat_gi BIGINT;
    store_id BIGINT;
    shelf_a BIGINT;
    shelf_b BIGINT;
    rack_a BIGINT;
    rack_b BIGINT;
    sup_med BIGINT;
    sup_link BIGINT;
    sup_health BIGINT;
    cash_id BIGINT;
    mobile_id BIGINT;
    m_amox BIGINT;
    m_para BIGINT;
    m_ibu BIGINT;
    m_omep BIGINT;
    m_met BIGINT;
    m_salb BIGINT;
    m_cet BIGINT;
    m_susp BIGINT;
    m_vitc BIGINT;
    m_asp BIGINT;
    m_azi BIGINT;
    m_ors BIGINT;
    c_amina BIGINT;
    c_john BIGINT;
    c_grace BIGINT;
    c_peter BIGINT;
    c_fatima BIGINT;
    c_samuel BIGINT;
    pur_id BIGINT;
    draft_id BIGINT;
    pi_amox BIGINT;
    pi_para BIGINT;
    pi_ibu BIGINT;
    pi_omep BIGINT;
    pi_met BIGINT;
    pi_salb BIGINT;
    pi_cet BIGINT;
    pi_susp BIGINT;
    pi_vitc BIGINT;
    pi_asp BIGINT;
    pi_azi BIGINT;
    pi_ors BIGINT;
    b_amox BIGINT;
    b_para BIGINT;
    b_ibu BIGINT;
    b_omep BIGINT;
    b_met BIGINT;
    b_salb BIGINT;
    b_cet BIGINT;
    b_susp BIGINT;
    b_vitc BIGINT;
    b_asp BIGINT;
    b_azi BIGINT;
    b_ors BIGINT;
    sale1 BIGINT;
    sale2 BIGINT;
    sale3 BIGINT;
    sale4 BIGINT;
    sale1_para BIGINT;
    rx_pending BIGINT;
    rx_reviewed BIGINT;
    rx_done BIGINT;
    rx_item BIGINT;
    received_at TIMESTAMPTZ := ((CURRENT_DATE - 10) + TIME '09:00') AT TIME ZONE 'UTC';
    older_sale_at TIMESTAMPTZ := ((CURRENT_DATE - 5) + TIME '14:20') AT TIME ZONE 'UTC';
    dispense_at TIMESTAMPTZ := ((CURRENT_DATE - 2) + TIME '10:15') AT TIME ZONE 'UTC';
BEGIN
    IF (SELECT COUNT(*) FROM medicines) > 0 THEN
        RAISE EXCEPTION 'Sample data was not loaded because medicines already exist.';
    END IF;

    SELECT id INTO admin_id FROM users WHERE username = 'admin';
    IF admin_id IS NULL THEN
        RAISE EXCEPTION 'Administrator account is missing.';
    END IF;

    SELECT id INTO cash_id FROM payment_methods WHERE code = 'CASH';
    SELECT id INTO mobile_id FROM payment_methods WHERE code = 'MOBILE_MONEY';

    INSERT INTO categories (name, description) VALUES
        ('Antibiotics', 'Antibacterial medicines'),
        ('Pain relief', 'Analgesics and anti-inflammatories'),
        ('Chronic care', 'Medicines for ongoing conditions'),
        ('Respiratory', 'Inhalers and cough or cold products'),
        ('Vitamins', 'Vitamins and supplements'),
        ('Gastrointestinal', 'Stomach and rehydration products')
    ;
    SELECT id INTO cat_abx FROM categories WHERE name = 'Antibiotics';
    SELECT id INTO cat_pain FROM categories WHERE name = 'Pain relief';
    SELECT id INTO cat_chronic FROM categories WHERE name = 'Chronic care';
    SELECT id INTO cat_resp FROM categories WHERE name = 'Respiratory';
    SELECT id INTO cat_vit FROM categories WHERE name = 'Vitamins';
    SELECT id INTO cat_gi FROM categories WHERE name = 'Gastrointestinal';

    INSERT INTO locations (location_type, code, name) VALUES ('STORE', 'MAIN', 'Main store') RETURNING id INTO store_id;
    INSERT INTO locations (parent_id, location_type, code, name) VALUES (store_id, 'SHELF', 'A', 'Shelf A') RETURNING id INTO shelf_a;
    INSERT INTO locations (parent_id, location_type, code, name) VALUES (store_id, 'SHELF', 'B', 'Shelf B') RETURNING id INTO shelf_b;
    INSERT INTO locations (parent_id, location_type, code, name) VALUES (shelf_a, 'RACK', 'A1', 'Rack A1') RETURNING id INTO rack_a;
    INSERT INTO locations (parent_id, location_type, code, name) VALUES (shelf_b, 'RACK', 'B1', 'Rack B1') RETURNING id INTO rack_b;

    INSERT INTO suppliers (name, contact_person, phone, email, address) VALUES
        ('MedSupply Ltd', 'Jane Kamau', '+254711100200', 'orders@medsupply.example', 'Industrial Area, Nairobi')
        RETURNING id INTO sup_med;
    INSERT INTO suppliers (name, contact_person, phone, email, address) VALUES
        ('PharmaLink Distributors', 'David Otieno', '+254722200300', 'sales@pharmalink.example', 'Mombasa Road, Nairobi')
        RETURNING id INTO sup_link;
    INSERT INTO suppliers (name, contact_person, phone, email, address) VALUES
        ('HealthSource Wholesale', 'Mary Wanjiku', '+254733300400', 'hello@healthsource.example', 'Kisumu CBD')
        RETURNING id INTO sup_health;

    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Amoxicillin', 'Amoxicillin', 'Amoxil', cat_abx, 'Capsule', '500 mg', 'Capsule', 'GSK', '890100100001', 30, 0.18, 0.45, rack_a) RETURNING id INTO m_amox;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Paracetamol', 'Paracetamol', 'Panadol', cat_pain, 'Tablet', '500 mg', 'Tablet', 'GSK', '890100100002', 80, 0.04, 0.12, rack_a) RETURNING id INTO m_para;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Ibuprofen', 'Ibuprofen', 'Brufen', cat_pain, 'Tablet', '400 mg', 'Tablet', 'Abbott', '890100100003', 40, 0.06, 0.18, rack_a) RETURNING id INTO m_ibu;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Omeprazole', 'Omeprazole', 'Losec', cat_gi, 'Capsule', '20 mg', 'Capsule', 'AstraZeneca', '890100100004', 20, 0.15, 0.40, rack_b) RETURNING id INTO m_omep;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Metformin', 'Metformin', 'Glucophage', cat_chronic, 'Tablet', '500 mg', 'Tablet', 'Merck', '890100100005', 40, 0.05, 0.15, rack_b) RETURNING id INTO m_met;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Salbutamol inhaler', 'Salbutamol', 'Ventolin', cat_resp, 'Inhaler', '100 mcg', 'Piece', 'GSK', '890100100006', 8, 3.50, 6.50, rack_b) RETURNING id INTO m_salb;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Cetirizine', 'Cetirizine', 'Zyrtec', cat_resp, 'Tablet', '10 mg', 'Tablet', 'UCB', '890100100007', 25, 0.03, 0.10, rack_a) RETURNING id INTO m_cet;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Amoxicillin suspension', 'Amoxicillin', 'Amoxil', cat_abx, 'Suspension', '250 mg/5 ml', 'Bottle', 'GSK', '890100100008', 12, 2.20, 4.80, rack_a) RETURNING id INTO m_susp;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Vitamin C', 'Ascorbic acid', 'Redoxon', cat_vit, 'Tablet', '1000 mg', 'Tablet', 'Bayer', '890100100009', 20, 0.08, 0.25, shelf_b) RETURNING id INTO m_vitc;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Aspirin', 'Acetylsalicylic acid', 'Disprin', cat_pain, 'Tablet', '300 mg', 'Tablet', 'Reckitt', '890100100010', 20, 0.03, 0.08, rack_a) RETURNING id INTO m_asp;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Azithromycin', 'Azithromycin', 'Zithromax', cat_abx, 'Tablet', '500 mg', 'Tablet', 'Pfizer', '890100100011', 15, 0.55, 1.40, rack_a) RETURNING id INTO m_azi;
    INSERT INTO medicines (name, generic_name, brand_name, category_id, dosage_form, strength, unit, manufacturer, barcode, reorder_level, purchase_price, selling_price, location_id)
    VALUES ('Oral rehydration salts', 'Oral rehydration salts', 'ORS', cat_gi, 'Powder', '20.5 g', 'Sachet', 'Universal', '890100100012', 30, 0.12, 0.35, shelf_b) RETURNING id INTO m_ors;

    INSERT INTO customers (reference, full_name, phone, email, address, date_of_birth, notes) VALUES
        ('CUST-' || key || '-0001', 'Amina Hassan', '+254701110001', 'amina.hassan@example.com', 'Eastleigh, Nairobi', '1992-04-12', 'Prefers evening pickup') RETURNING id INTO c_amina;
    INSERT INTO customers (reference, full_name, phone, email, address, date_of_birth) VALUES
        ('CUST-' || key || '-0002', 'John Okello', '+254701110002', 'john.okello@example.com', 'Kisumu', '1984-11-03') RETURNING id INTO c_john;
    INSERT INTO customers (reference, full_name, phone, address, date_of_birth) VALUES
        ('CUST-' || key || '-0003', 'Grace Mwangi', '+254701110003', 'Nakuru', '1978-01-19') RETURNING id INTO c_grace;
    INSERT INTO customers (reference, full_name, phone, address) VALUES
        ('CUST-' || key || '-0004', 'Peter Njoroge', '+254701110004', 'Thika') RETURNING id INTO c_peter;
    INSERT INTO customers (reference, full_name, phone, email, address, date_of_birth) VALUES
        ('CUST-' || key || '-0005', 'Fatima Ali', '+254701110005', 'fatima.ali@example.com', 'Mombasa', '2001-07-28') RETURNING id INTO c_fatima;
    INSERT INTO customers (reference, full_name, phone, address, notes) VALUES
        ('CUST-' || key || '-0006', 'Samuel Kariuki', '+254701110006', 'Nyeri', 'Regular metformin patient') RETURNING id INTO c_samuel;

    INSERT INTO purchases (reference, supplier_id, purchase_date, status, payment_status, total_amount, amount_paid, notes, created_by, confirmed_by, confirmed_at, created_at)
    VALUES ('PUR-' || key || '-0001', sup_med, day - 10, 'CONFIRMED', 'PAID', 0, 0, 'Opening delivery', admin_id, admin_id, received_at, received_at)
    RETURNING id INTO pur_id;

    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_amox, 200, 0.18, 0.45, 'AMX-2401', day - 120, day + 540, rack_a, 36.00) RETURNING id INTO pi_amox;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_para, 500, 0.04, 0.12, 'PAR-2408', day - 90, day + 600, rack_a, 20.00) RETURNING id INTO pi_para;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_ibu, 20, 0.06, 0.18, 'IBU-2411', day - 60, day + 400, rack_a, 1.20) RETURNING id INTO pi_ibu;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_omep, 80, 0.15, 0.40, 'OME-2509', day - 300, day + 40, rack_b, 12.00) RETURNING id INTO pi_omep;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_met, 300, 0.05, 0.15, 'MET-2410', day - 100, day + 500, rack_b, 15.00) RETURNING id INTO pi_met;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_salb, 10, 3.50, 6.50, 'SAL-2406', day - 200, day + 300, rack_b, 35.00) RETURNING id INTO pi_salb;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_cet, 160, 0.03, 0.10, 'CET-2412', day - 80, day + 450, rack_a, 4.80) RETURNING id INTO pi_cet;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_susp, 24, 2.20, 4.80, 'AMS-2407', day - 70, day + 200, rack_a, 52.80) RETURNING id INTO pi_susp;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_vitc, 120, 0.08, 0.25, 'VTC-2501', day - 40, day + 700, shelf_b, 9.60) RETURNING id INTO pi_vitc;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_asp, 40, 0.03, 0.08, 'ASP-2302', day - 800, day - 20, rack_a, 1.20) RETURNING id INTO pi_asp;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_azi, 60, 0.55, 1.40, 'AZI-2410', day - 50, day + 480, rack_a, 33.00) RETURNING id INTO pi_azi;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (pur_id, m_ors, 200, 0.12, 0.35, 'ORS-2502', day - 30, day + 360, shelf_b, 24.00) RETURNING id INTO pi_ors;

    UPDATE purchases SET total_amount = (SELECT SUM(line_total) FROM purchase_items WHERE purchase_id = pur_id),
        amount_paid = (SELECT SUM(line_total) FROM purchase_items WHERE purchase_id = pur_id)
    WHERE id = pur_id;

    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_amox, 'AMX-2401', day - 120, day + 540, 200, 190, 0.18, 0.45, sup_med, rack_a, pi_amox, received_at) RETURNING id INTO b_amox;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_para, 'PAR-2408', day - 90, day + 600, 500, 460, 0.04, 0.12, sup_med, rack_a, pi_para, received_at) RETURNING id INTO b_para;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_ibu, 'IBU-2411', day - 60, day + 400, 20, 12, 0.06, 0.18, sup_med, rack_a, pi_ibu, received_at) RETURNING id INTO b_ibu;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_omep, 'OME-2509', day - 300, day + 40, 80, 80, 0.15, 0.40, sup_med, rack_b, pi_omep, received_at) RETURNING id INTO b_omep;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_met, 'MET-2410', day - 100, day + 500, 300, 286, 0.05, 0.15, sup_med, rack_b, pi_met, received_at) RETURNING id INTO b_met;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_salb, 'SAL-2406', day - 200, day + 300, 10, 6, 3.50, 6.50, sup_med, rack_b, pi_salb, received_at) RETURNING id INTO b_salb;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_cet, 'CET-2412', day - 80, day + 450, 160, 150, 0.03, 0.10, sup_med, rack_a, pi_cet, received_at) RETURNING id INTO b_cet;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_susp, 'AMS-2407', day - 70, day + 200, 24, 0, 2.20, 4.80, sup_med, rack_a, pi_susp, received_at) RETURNING id INTO b_susp;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_vitc, 'VTC-2501', day - 40, day + 700, 120, 120, 0.08, 0.25, sup_med, shelf_b, pi_vitc, received_at) RETURNING id INTO b_vitc;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_asp, 'ASP-2302', day - 800, day - 20, 40, 40, 0.03, 0.08, sup_med, rack_a, pi_asp, received_at) RETURNING id INTO b_asp;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_azi, 'AZI-2410', day - 50, day + 480, 60, 60, 0.55, 1.40, sup_med, rack_a, pi_azi, received_at) RETURNING id INTO b_azi;
    INSERT INTO batches (medicine_id, batch_number, manufacturing_date, expiry_date, quantity_received, quantity_on_hand, purchase_price, selling_price, supplier_id, location_id, purchase_item_id, created_at)
    VALUES (m_ors, 'ORS-2502', day - 30, day + 360, 200, 200, 0.12, 0.35, sup_med, shelf_b, pi_ors, received_at) RETURNING id INTO b_ors;

    INSERT INTO stock_movements (medicine_id, batch_id, movement_type, quantity, direction, balance_after, reference_type, reference_id, reason, performed_by, created_at)
    SELECT medicine_id, id, 'RECEIVED', quantity_received, 'IN', quantity_received, 'PURCHASE', pur_id, 'Purchase received', admin_id, received_at
    FROM batches;

    INSERT INTO purchases (reference, supplier_id, purchase_date, status, payment_status, total_amount, amount_paid, notes, created_by)
    VALUES ('PUR-' || key || '-0002', sup_health, day, 'DRAFT', 'UNPAID', 18.00, 0, 'Waiting for the delivery note', admin_id)
    RETURNING id INTO draft_id;
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (draft_id, m_para, 200, 0.04, 0.12, 'PAR-2601', day - 15, day + 700, rack_a, 8.00);
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (draft_id, m_vitc, 100, 0.08, 0.25, 'VTC-2601', day - 15, day + 700, shelf_b, 8.00);
    INSERT INTO purchase_items (purchase_id, medicine_id, quantity, purchase_price, selling_price, batch_number, manufacturing_date, expiry_date, location_id, line_total)
    VALUES (draft_id, m_ors, 50, 0.12, 0.35, 'ORS-2601', day - 10, day + 400, shelf_b, 6.00);
    UPDATE purchases SET total_amount = 22.00 WHERE id = draft_id;

    INSERT INTO sales (reference, customer_id, status, subtotal, discount_amount, total_amount, cashier_id, created_at)
    VALUES ('SAL-' || past_key || '-0001', c_grace, 'COMPLETED', 1.80, 0, 1.80, admin_id, older_sale_at) RETURNING id INTO sale4;
    INSERT INTO sale_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_amount, line_total)
    VALUES (sale4, m_para, b_para, 15, 0.12, 0, 1.80);
    INSERT INTO payments (sale_id, payment_method_id, amount, tendered_amount, change_amount, paid_at, received_by)
    VALUES (sale4, cash_id, 1.80, 2.00, 0.20, older_sale_at, admin_id);
    INSERT INTO stock_movements (medicine_id, batch_id, movement_type, quantity, direction, balance_after, reference_type, reference_id, reason, performed_by, created_at)
    VALUES (m_para, b_para, 'SOLD', 15, 'OUT', 485, 'SALE', sale4, 'Sale', admin_id, older_sale_at);

    INSERT INTO prescriptions (reference, customer_id, prescription_date, prescriber_name, prescriber_license, status, notes, created_by, reviewed_by, reviewed_at, created_at)
    VALUES ('RX-' || to_char(day - 2, 'YYYYMMDD') || '-0001', c_samuel, day - 2, 'Dr. A. Patel', 'MD-10442', 'FULLY_DISPENSED', 'Continue with meals', admin_id, admin_id, dispense_at, dispense_at)
    RETURNING id INTO rx_done;
    INSERT INTO prescription_items (prescription_id, medicine_id, dosage, frequency, duration, quantity, instructions, quantity_dispensed)
    VALUES (rx_done, m_met, '500 mg', 'Twice daily', '7 days', 14, 'Take with food', 14) RETURNING id INTO rx_item;
    INSERT INTO dispensings (reference, prescription_id, dispensed_by, dispensed_at, notes)
    VALUES ('DSP-' || to_char(day - 2, 'YYYYMMDD') || '-0001', rx_done, admin_id, dispense_at, 'Counseled on taking with meals') RETURNING id INTO rx_pending;
    INSERT INTO dispensing_items (dispensing_id, prescription_item_id, batch_id, quantity)
    VALUES (rx_pending, rx_item, b_met, 14);
    INSERT INTO stock_movements (medicine_id, batch_id, movement_type, quantity, direction, balance_after, reference_type, reference_id, reason, performed_by, created_at)
    VALUES (m_met, b_met, 'DISPENSED', 14, 'OUT', 286, 'DISPENSING', rx_pending, 'Dispensed', admin_id, dispense_at);

    INSERT INTO sales (reference, customer_id, status, subtotal, discount_amount, total_amount, cashier_id, created_at)
    VALUES ('SAL-' || key || '-0001', c_amina, 'COMPLETED', 7.50, 0, 7.50, admin_id, NOW() - INTERVAL '3 hours') RETURNING id INTO sale1;
    INSERT INTO sale_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_amount, line_total)
    VALUES (sale1, m_para, b_para, 25, 0.12, 0, 3.00) RETURNING id INTO sale1_para;
    INSERT INTO sale_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_amount, line_total)
    VALUES (sale1, m_amox, b_amox, 10, 0.45, 0, 4.50);
    INSERT INTO payments (sale_id, payment_method_id, amount, tendered_amount, change_amount, paid_at, received_by)
    VALUES (sale1, cash_id, 7.50, 10.00, 2.50, NOW() - INTERVAL '3 hours', admin_id);
    INSERT INTO stock_movements (medicine_id, batch_id, movement_type, quantity, direction, balance_after, reference_type, reference_id, reason, performed_by, created_at)
    VALUES
        (m_para, b_para, 'SOLD', 25, 'OUT', 460, 'SALE', sale1, 'Sale', admin_id, NOW() - INTERVAL '3 hours'),
        (m_amox, b_amox, 'SOLD', 10, 'OUT', 190, 'SALE', sale1, 'Sale', admin_id, NOW() - INTERVAL '3 hours');

    INSERT INTO sales (reference, status, subtotal, discount_amount, total_amount, cashier_id, created_at)
    VALUES ('SAL-' || key || '-0002', 'COMPLETED', 27.44, 0, 27.44, admin_id, NOW() - INTERVAL '2 hours') RETURNING id INTO sale2;
    INSERT INTO sale_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_amount, line_total)
    VALUES (sale2, m_ibu, b_ibu, 8, 0.18, 0, 1.44);
    INSERT INTO sale_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_amount, line_total)
    VALUES (sale2, m_salb, b_salb, 4, 6.50, 0, 26.00);
    INSERT INTO payments (sale_id, payment_method_id, amount, tendered_amount, change_amount, paid_at, received_by)
    VALUES (sale2, cash_id, 27.44, 30.00, 2.56, NOW() - INTERVAL '2 hours', admin_id);
    INSERT INTO stock_movements (medicine_id, batch_id, movement_type, quantity, direction, balance_after, reference_type, reference_id, reason, performed_by, created_at)
    VALUES
        (m_ibu, b_ibu, 'SOLD', 8, 'OUT', 12, 'SALE', sale2, 'Sale', admin_id, NOW() - INTERVAL '2 hours'),
        (m_salb, b_salb, 'SOLD', 4, 'OUT', 6, 'SALE', sale2, 'Sale', admin_id, NOW() - INTERVAL '2 hours');

    INSERT INTO sales (reference, customer_id, status, subtotal, discount_amount, total_amount, cashier_id, created_at)
    VALUES ('SAL-' || key || '-0003', c_john, 'COMPLETED', 116.20, 0, 116.20, admin_id, NOW() - INTERVAL '40 minutes') RETURNING id INTO sale3;
    INSERT INTO sale_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_amount, line_total)
    VALUES (sale3, m_susp, b_susp, 24, 4.80, 0, 115.20);
    INSERT INTO sale_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_amount, line_total)
    VALUES (sale3, m_cet, b_cet, 10, 0.10, 0, 1.00);
    INSERT INTO payments (sale_id, payment_method_id, amount, reference, paid_at, received_by)
    VALUES (sale3, mobile_id, 116.20, 'MM-88421', NOW() - INTERVAL '40 minutes', admin_id);
    INSERT INTO stock_movements (medicine_id, batch_id, movement_type, quantity, direction, balance_after, reference_type, reference_id, reason, performed_by, created_at)
    VALUES
        (m_susp, b_susp, 'SOLD', 24, 'OUT', 0, 'SALE', sale3, 'Sale', admin_id, NOW() - INTERVAL '40 minutes'),
        (m_cet, b_cet, 'SOLD', 10, 'OUT', 150, 'SALE', sale3, 'Sale', admin_id, NOW() - INTERVAL '40 minutes');

    INSERT INTO sale_returns (reference, sale_id, status, reason, refund_amount, refund_method_id, created_by)
    VALUES ('RET-' || key || '-0001', sale1, 'PENDING', 'Customer bought more paracetamol than needed', 0.24, cash_id, admin_id);
    INSERT INTO return_items (return_id, sale_item_id, batch_id, quantity, refund_amount)
    SELECT id, sale1_para, b_para, 2, 0.24 FROM sale_returns WHERE reference = 'RET-' || key || '-0001';

    INSERT INTO prescriptions (reference, customer_id, prescription_date, prescriber_name, prescriber_license, status, notes, created_by)
    VALUES ('RX-' || key || '-0001', c_fatima, day, 'Dr. A. Patel', 'MD-10442', 'PENDING', 'Check antibiotic history before review', admin_id);
    INSERT INTO prescription_items (prescription_id, medicine_id, dosage, frequency, duration, quantity, instructions)
    SELECT id, m_azi, '500 mg', 'Once daily', '3 days', 3, 'Take one hour before food' FROM prescriptions WHERE reference = 'RX-' || key || '-0001';

    INSERT INTO prescriptions (reference, customer_id, prescription_date, prescriber_name, prescriber_license, status, created_by, reviewed_by, reviewed_at)
    VALUES ('RX-' || key || '-0002', c_peter, day, 'Dr. L. Nguyen', 'MD-22810', 'REVIEWED', admin_id, admin_id, NOW() - INTERVAL '1 hour')
    RETURNING id INTO rx_reviewed;
    INSERT INTO prescription_items (prescription_id, medicine_id, dosage, frequency, duration, quantity, instructions)
    VALUES (rx_reviewed, m_omep, '20 mg', 'Once daily', '14 days', 14, 'Take before breakfast');

    INSERT INTO users (username, email, password_hash, full_name, phone, active, must_change_password)
    SELECT 'pharmacist', 'pharmacist@pharmacy.local', crypt('Pharmacist1', gen_salt('bf', 10)), 'Aisha Mohamed', '+254700100001', TRUE, FALSE
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'pharmacist');
    INSERT INTO users (username, email, password_hash, full_name, phone, active, must_change_password)
    SELECT 'cashier', 'cashier@pharmacy.local', crypt('Cashier123', gen_salt('bf', 10)), 'Brian Otieno', '+254700100002', TRUE, FALSE
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'cashier');
    INSERT INTO users (username, email, password_hash, full_name, phone, active, must_change_password)
    SELECT 'storekeeper', 'storekeeper@pharmacy.local', crypt('Store12345', gen_salt('bf', 10)), 'Lucy Wanjiru', '+254700100003', TRUE, FALSE
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'storekeeper');

    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'PHARMACIST' WHERE u.username = 'pharmacist'
    ON CONFLICT DO NOTHING;
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'CASHIER' WHERE u.username = 'cashier'
    ON CONFLICT DO NOTHING;
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'STOREKEEPER' WHERE u.username = 'storekeeper'
    ON CONFLICT DO NOTHING;

    INSERT INTO document_sequences (seq_key, last_value) VALUES
        ('CUST-' || key, 6),
        ('PUR-' || key, 2),
        ('SAL-' || key, 3),
        ('SAL-' || past_key, 1),
        ('RX-' || key, 2),
        ('RX-' || to_char(day - 2, 'YYYYMMDD'), 1),
        ('DSP-' || to_char(day - 2, 'YYYYMMDD'), 1),
        ('RET-' || key, 1)
    ON CONFLICT (seq_key) DO UPDATE SET last_value = GREATEST(document_sequences.last_value, EXCLUDED.last_value);

    UPDATE system_settings SET setting_value = 'Hangou Memorial Hospital', updated_at = NOW()
    WHERE setting_key = 'pharmacy.name' AND setting_value IN ('Pharmacy', 'Green Cross Pharmacy');
    UPDATE system_settings SET setting_value = '12 Market Street, Nairobi', updated_at = NOW()
    WHERE setting_key = 'pharmacy.address' AND setting_value = '';
    UPDATE system_settings SET setting_value = '+254 700 000 111', updated_at = NOW()
    WHERE setting_key = 'pharmacy.phone' AND setting_value = '';

    INSERT INTO audit_logs (user_id, username, action, entity_type, entity_id, details)
    VALUES
        (admin_id, 'admin', 'PURCHASE_CONFIRM', 'PURCHASE', pur_id::text, jsonb_build_object('reference', 'PUR-' || key || '-0001')),
        (admin_id, 'admin', 'SALE_COMPLETE', 'SALE', sale1::text, jsonb_build_object('reference', 'SAL-' || key || '-0001')),
        (admin_id, 'admin', 'DISPENSE', 'DISPENSING', rx_pending::text, jsonb_build_object('reference', 'DSP-' || to_char(day - 2, 'YYYYMMDD') || '-0001'));
END $$;

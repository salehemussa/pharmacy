package com.pharmacy.reporting;

import com.pharmacy.common.BusinessException;
import com.pharmacy.common.Csv;
import com.pharmacy.common.Money;
import com.pharmacy.inventory.InventoryService;
import com.pharmacy.inventory.StockMovementRepository;
import com.pharmacy.settings.SettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final EntityManager entityManager;
    private final SettingsService settingsService;
    private final InventoryService inventoryService;
    private final StockMovementRepository movements;

    @Transactional(readOnly = true)
    public ResponseEntity<?> sales(String granularity, LocalDate from, LocalDate to, String format) {
        String unit = switch (granularity == null ? "day" : granularity.toLowerCase()) {
            case "day" -> "day";
            case "week" -> "week";
            case "month" -> "month";
            default -> throw BusinessException.badRequest("INVALID_PERIOD", "Group sales by day, week or month.");
        };
        Range range = range(from, to);
        String period = unit.equals("day")
                ? "(s.created_at AT TIME ZONE :tz)::date"
                : "date_trunc('" + unit + "', s.created_at AT TIME ZONE :tz)::date";
        Query query = entityManager.createNativeQuery("""
                SELECT %s AS period, COUNT(*) AS sale_count, COALESCE(SUM(s.total_amount), 0) AS total_amount
                FROM sales s
                WHERE s.status = 'COMPLETED' AND s.created_at >= :fromTs AND s.created_at < :toTs
                GROUP BY 1
                ORDER BY 1
                """.formatted(period));
        query.setParameter("tz", settingsService.zone().getId());
        query.setParameter("fromTs", range.fromInstant());
        query.setParameter("toTs", range.toInstant());
        return table("sales-" + unit + ".csv", List.of("period", "saleCount", "totalAmount"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> salesByMedicine(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT m.name, SUM(si.quantity) AS quantity, COALESCE(SUM(si.line_total), 0) AS total_amount
                FROM sale_items si
                JOIN sales s ON s.id = si.sale_id
                JOIN medicines m ON m.id = si.medicine_id
                WHERE s.status = 'COMPLETED' AND s.created_at >= :fromTs AND s.created_at < :toTs
                GROUP BY m.name
                ORDER BY total_amount DESC, m.name
                """);
        bind(query, range);
        return table("sales-by-medicine.csv", List.of("medicine", "quantity", "totalAmount"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> salesByUser(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT u.username, COUNT(*) AS sale_count, COALESCE(SUM(s.total_amount), 0) AS total_amount
                FROM sales s JOIN users u ON u.id = s.cashier_id
                WHERE s.status = 'COMPLETED' AND s.created_at >= :fromTs AND s.created_at < :toTs
                GROUP BY u.username
                ORDER BY total_amount DESC
                """);
        bind(query, range);
        return table("sales-by-user.csv", List.of("user", "saleCount", "totalAmount"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> salesByPayment(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT pm.name, COUNT(*) AS payment_count, COALESCE(SUM(p.amount), 0) AS total_amount
                FROM payments p
                JOIN payment_methods pm ON pm.id = p.payment_method_id
                JOIN sales s ON s.id = p.sale_id
                WHERE s.status = 'COMPLETED' AND p.paid_at >= :fromTs AND p.paid_at < :toTs
                GROUP BY pm.name
                ORDER BY total_amount DESC
                """);
        bind(query, range);
        return table("sales-by-payment-method.csv", List.of("paymentMethod", "paymentCount", "totalAmount"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> stock(String status, String format) {
        List<InventoryService.StockRow> rows = inventoryService.allStock(status == null ? "ALL" : status);
        List<List<String>> body = rows.stream().map(row -> List.of(
                row.name(), row.strength(), row.dosageForm(), String.valueOf(row.sellableQuantity()),
                String.valueOf(row.onHandQuantity()), String.valueOf(row.expiredQuantity()),
                row.sellableValue().toPlainString(), row.locationPath() == null ? "" : row.locationPath()
        )).toList();
        return table("stock.csv", List.of("medicine", "strength", "dosageForm", "sellableQuantity", "onHandQuantity", "expiredQuantity", "sellableValue", "location"), body, format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> stockMovements(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT sm.created_at, m.name, b.batch_number, sm.movement_type, sm.direction, sm.quantity, sm.balance_after, u.username
                FROM stock_movements sm
                JOIN medicines m ON m.id = sm.medicine_id
                JOIN batches b ON b.id = sm.batch_id
                JOIN users u ON u.id = sm.performed_by
                WHERE sm.created_at >= :fromTs AND sm.created_at < :toTs
                ORDER BY sm.created_at DESC
                """);
        bind(query, range);
        return table("stock-movements.csv", List.of("createdAt", "medicine", "batch", "type", "direction", "quantity", "balanceAfter", "user"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> adjustments(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT sm.created_at, m.name, b.batch_number, sm.movement_type, sm.direction, sm.quantity, COALESCE(sm.reason, ''), u.username
                FROM stock_movements sm
                JOIN medicines m ON m.id = sm.medicine_id
                JOIN batches b ON b.id = sm.batch_id
                JOIN users u ON u.id = sm.performed_by
                WHERE sm.movement_type IN ('ADJUSTED', 'DAMAGED', 'EXPIRED')
                  AND sm.created_at >= :fromTs AND sm.created_at < :toTs
                ORDER BY sm.created_at DESC
                """);
        bind(query, range);
        return table("stock-adjustments.csv", List.of("createdAt", "medicine", "batch", "type", "direction", "quantity", "reason", "user"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> purchases(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT p.purchase_date, p.reference, s.name, p.status, p.total_amount
                FROM purchases p JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.purchase_date >= :fromDate AND p.purchase_date <= :toDate
                ORDER BY p.purchase_date, p.reference
                """);
        query.setParameter("fromDate", range.from());
        query.setParameter("toDate", range.to());
        return table("purchases.csv", List.of("date", "reference", "supplier", "status", "totalAmount"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> purchasesBySupplier(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT s.name, COUNT(*) AS purchase_count, COALESCE(SUM(p.total_amount), 0) AS total_amount
                FROM purchases p JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.status = 'CONFIRMED' AND p.purchase_date >= :fromDate AND p.purchase_date <= :toDate
                GROUP BY s.name
                ORDER BY total_amount DESC
                """);
        query.setParameter("fromDate", range.from());
        query.setParameter("toDate", range.to());
        return table("purchases-by-supplier.csv", List.of("supplier", "purchaseCount", "totalAmount"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> prescriptions(String status, LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (statusFilter != null && !List.of("PENDING", "REVIEWED", "PARTIALLY_DISPENSED", "FULLY_DISPENSED", "CANCELLED").contains(statusFilter)) {
            throw BusinessException.badRequest("INVALID_STATUS", "Unknown prescription status.");
        }
        Query query = entityManager.createNativeQuery("""
                SELECT p.reference, c.full_name, p.prescription_date, p.status, p.prescriber_name
                FROM prescriptions p JOIN customers c ON c.id = p.customer_id
                WHERE p.prescription_date >= :fromDate AND p.prescription_date <= :toDate
                  AND (CAST(:status AS varchar) IS NULL OR p.status = CAST(:status AS varchar))
                ORDER BY p.prescription_date DESC, p.reference
                """);
        query.setParameter("fromDate", range.from());
        query.setParameter("toDate", range.to());
        query.setParameter("status", statusFilter);
        return table("prescriptions.csv", List.of("reference", "customer", "date", "status", "prescriber"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> dispensing(LocalDate from, LocalDate to, String format) {
        Range range = range(from, to);
        Query query = entityManager.createNativeQuery("""
                SELECT d.dispensed_at, d.reference, p.reference AS prescription_ref, m.name, b.batch_number, di.quantity, u.username
                FROM dispensing_items di
                JOIN dispensings d ON d.id = di.dispensing_id
                JOIN prescriptions p ON p.id = d.prescription_id
                JOIN prescription_items pi ON pi.id = di.prescription_item_id
                JOIN medicines m ON m.id = pi.medicine_id
                JOIN batches b ON b.id = di.batch_id
                JOIN users u ON u.id = d.dispensed_by
                WHERE d.dispensed_at >= :fromTs AND d.dispensed_at < :toTs
                ORDER BY d.dispensed_at DESC
                """);
        bind(query, range);
        return table("dispensing.csv", List.of("dispensedAt", "dispensing", "prescription", "medicine", "batch", "quantity", "pharmacist"), rows(query.getResultList()), format);
    }

    @Transactional(readOnly = true)
    public ManagementSummary management(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        BigDecimal salesTotal = single("""
                SELECT COALESCE(SUM(total_amount), 0) FROM sales
                WHERE status = 'COMPLETED' AND created_at >= :fromTs AND created_at < :toTs
                """, range, false);
        BigDecimal returnsTotal = single("""
                SELECT COALESCE(SUM(refund_amount), 0) FROM sale_returns
                WHERE status = 'APPROVED' AND approved_at >= :fromTs AND approved_at < :toTs
                """, range, false);
        BigDecimal purchaseTotal = single("""
                SELECT COALESCE(SUM(total_amount), 0) FROM purchases
                WHERE status = 'CONFIRMED' AND purchase_date >= :fromDate AND purchase_date <= :toDate
                """, range, true);
        List<InventoryService.StockRow> stock = inventoryService.allStock("ALL");
        BigDecimal stockValue = stock.stream().map(InventoryService.StockRow::sellableValue).reduce(Money.zero(), BigDecimal::add);
        return new ManagementSummary(range.from(), range.to(), money(salesTotal), money(returnsTotal), money(salesTotal.subtract(returnsTotal)), money(purchaseTotal), money(stockValue), movements.count());
    }

    private BigDecimal single(String sql, Range range, boolean byDate) {
        Query query = entityManager.createNativeQuery(sql);
        if (byDate) {
            query.setParameter("fromDate", range.from());
            query.setParameter("toDate", range.to());
        } else {
            bind(query, range);
        }
        return money(new BigDecimal(query.getSingleResult().toString()));
    }

    private void bind(Query query, Range range) {
        query.setParameter("fromTs", range.fromInstant());
        query.setParameter("toTs", range.toInstant());
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? settingsService.today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) {
            throw BusinessException.badRequest("INVALID_RANGE", "The start date must be on or before the end date.");
        }
        ZoneId zone = settingsService.zone();
        return new Range(start, end, start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private List<List<String>> rows(List<?> result) {
        List<List<String>> rows = new ArrayList<>();
        for (Object row : result) {
            Object[] values = row instanceof Object[] array ? array : new Object[]{row};
            List<String> cells = new ArrayList<>();
            for (Object value : values) {
                cells.add(value == null ? "" : value.toString());
            }
            rows.add(cells);
        }
        return rows;
    }

    private ResponseEntity<?> table(String filename, List<String> columns, List<List<String>> body, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(Csv.of(columns, body));
        }
        List<java.util.Map<String, String>> data = new ArrayList<>();
        for (List<String> row : body) {
            java.util.Map<String, String> mapped = new java.util.LinkedHashMap<>();
            for (int index = 0; index < columns.size(); index++) {
                mapped.put(columns.get(index), index < row.size() ? row.get(index) : "");
            }
            data.add(mapped);
        }
        return ResponseEntity.ok(new ReportTable(columns, data));
    }

    private BigDecimal money(BigDecimal value) {
        return Money.of(value);
    }

    private record Range(LocalDate from, LocalDate to, Instant fromInstant, Instant toInstant) {
    }

    public record ReportTable(List<String> columns, List<java.util.Map<String, String>> rows) {
    }

    public record ManagementSummary(
            LocalDate from, LocalDate to, BigDecimal grossSales, BigDecimal approvedReturns, BigDecimal netSales,
            BigDecimal confirmedPurchases, BigDecimal sellableStockValue, long stockMovements
    ) {
    }
}

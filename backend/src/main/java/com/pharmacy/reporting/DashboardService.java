package com.pharmacy.reporting;

import com.pharmacy.common.Money;
import com.pharmacy.common.PrescriptionStatus;
import com.pharmacy.common.PurchaseStatus;
import com.pharmacy.common.SaleStatus;
import com.pharmacy.inventory.Batch;
import com.pharmacy.inventory.BatchRepository;
import com.pharmacy.inventory.InventoryService;
import com.pharmacy.prescription.PrescriptionRepository;
import com.pharmacy.purchase.Purchase;
import com.pharmacy.purchase.PurchaseRepository;
import com.pharmacy.sale.SaleRepository;
import com.pharmacy.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SaleRepository sales;
    private final PurchaseRepository purchases;
    private final PrescriptionRepository prescriptions;
    private final BatchRepository batches;
    private final InventoryService inventoryService;
    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        ZoneId zone = settingsService.zone();
        LocalDate today = settingsService.today();
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<InventoryService.StockRow> stock = inventoryService.allStock("ALL");
        BigDecimal sellableValue = stock.stream().map(InventoryService.StockRow::sellableValue).reduce(Money.zero(), BigDecimal::add);
        long low = stock.stream().filter(row -> row.sellableQuantity() > 0 && row.sellableQuantity() <= row.reorderLevel()).count();
        long out = stock.stream().filter(row -> row.sellableQuantity() == 0).count();
        long expiredMedicines = stock.stream().filter(row -> row.expiredQuantity() > 0).count();
        long near = stock.stream().filter(InventoryService.StockRow::nearExpiry).count();
        List<Batch> expiredBatches = batches.findExpired(today);
        List<Batch> nearBatches = batches.findNearExpiry(today, today.plusDays(settingsService.getInt("inventory.expiry_warning_days")));
        long pending = prescriptions.countByStatusIn(List.of(PrescriptionStatus.PENDING, PrescriptionStatus.REVIEWED, PrescriptionStatus.PARTIALLY_DISPENSED));
        return new DashboardResponse(
                money(sales.sumTotal(SaleStatus.COMPLETED, start, end)),
                money(sales.sumAll(SaleStatus.COMPLETED)),
                money(sellableValue),
                stock.size(),
                low,
                out,
                expiredMedicines,
                expiredBatches.stream().mapToInt(Batch::getQuantityOnHand).sum(),
                near,
                pending,
                sales.findAll((root, query, cb) -> cb.equal(root.get("status"), SaleStatus.COMPLETED), PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(sale -> new RecentSale(sale.getId(), sale.getReference(), sale.getTotalAmount(), sale.getCashier().getUsername(), sale.getCreatedAt())).getContent(),
                purchases.findAll((root, query, cb) -> cb.conjunction(), PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(this::recentPurchase).getContent(),
                alerts(low, out, expiredBatches.size(), nearBatches.size(), pending)
        );
    }

    private RecentPurchase recentPurchase(Purchase purchase) {
        return new RecentPurchase(purchase.getId(), purchase.getReference(), purchase.getSupplier().getName(), purchase.getTotalAmount(), purchase.getStatus(), purchase.getPurchaseDate());
    }

    private List<AlertItem> alerts(long low, long out, long expired, long near, long pending) {
        List<AlertItem> items = new java.util.ArrayList<>();
        if (expired > 0) {
            items.add(new AlertItem("EXPIRED", "HIGH", expired + " batch(es) are expired and still in stock."));
        }
        if (out > 0) {
            items.add(new AlertItem("OUT_OF_STOCK", "HIGH", out + " medicine(s) have no sellable stock."));
        }
        if (low > 0) {
            items.add(new AlertItem("LOW_STOCK", "MEDIUM", low + " medicine(s) are at or below the reorder level."));
        }
        if (near > 0) {
            items.add(new AlertItem("NEAR_EXPIRY", "MEDIUM", near + " medicine(s) have stock approaching expiry."));
        }
        if (pending > 0) {
            items.add(new AlertItem("PENDING_PRESCRIPTIONS", "LOW", pending + " prescription(s) are waiting for review or dispensing."));
        }
        return items;
    }

    private BigDecimal money(BigDecimal value) {
        return Money.of(value);
    }

    public record RecentSale(Long id, String reference, BigDecimal totalAmount, String cashier, Instant createdAt) {
    }

    public record RecentPurchase(Long id, String reference, String supplierName, BigDecimal totalAmount, PurchaseStatus status, LocalDate purchaseDate) {
    }

    public record AlertItem(String type, String severity, String message) {
    }

    public record DashboardResponse(
            BigDecimal todaySales,
            BigDecimal totalSales,
            BigDecimal sellableStockValue,
            long medicineCount,
            long lowStockCount,
            long outOfStockCount,
            long medicinesWithExpiredStock,
            long expiredQuantity,
            long nearExpiryCount,
            long pendingPrescriptions,
            List<RecentSale> recentSales,
            List<RecentPurchase> recentPurchases,
            List<AlertItem> alerts
    ) {
    }
}

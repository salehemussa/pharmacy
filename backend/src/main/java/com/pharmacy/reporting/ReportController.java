package com.pharmacy.reporting;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final DashboardService dashboardService;
    private final ReportService reportService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public DashboardService.DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public java.util.List<DashboardService.AlertItem> alerts() {
        return dashboardService.dashboard().alerts();
    }

    @GetMapping("/reports/sales")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> sales(
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String format
    ) {
        return reportService.sales(granularity, from, to, format);
    }

    @GetMapping("/reports/sales/by-medicine")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> salesByMedicine(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.salesByMedicine(from, to, format);
    }

    @GetMapping("/reports/sales/by-user")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> salesByUser(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.salesByUser(from, to, format);
    }

    @GetMapping("/reports/sales/by-payment-method")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> salesByPayment(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.salesByPayment(from, to, format);
    }

    @GetMapping("/reports/stock")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> stock(@RequestParam(defaultValue = "ALL") String status, @RequestParam(required = false) String format) {
        return reportService.stock(status, format);
    }

    @GetMapping("/reports/stock/movements")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> movements(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.stockMovements(from, to, format);
    }

    @GetMapping("/reports/stock/adjustments")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> adjustments(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.adjustments(from, to, format);
    }

    @GetMapping("/reports/purchases")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> purchases(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.purchases(from, to, format);
    }

    @GetMapping("/reports/purchases/by-supplier")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> purchasesBySupplier(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.purchasesBySupplier(from, to, format);
    }

    @GetMapping("/reports/prescriptions")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> prescriptions(@RequestParam(required = false) String status, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.prescriptions(status, from, to, format);
    }

    @GetMapping("/reports/dispensing")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<?> dispensing(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format) {
        return reportService.dispensing(from, to, format);
    }

    @GetMapping("/reports/management")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ReportService.ManagementSummary management(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return reportService.management(from, to);
    }
}

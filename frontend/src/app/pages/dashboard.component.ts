import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { Dashboard, ReportTable, StockRow } from '../core/models';
import { errorMessage, money as formatMoney, statusLabel, when } from '../core/format';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  template: `
    <section class="page">
      @if (loading()) {
        <div class="cards">@for (item of [1,2,3,4,5,6]; track item) { <div class="skeleton"></div> }</div>
      }
      @if (error()) { <p class="err">{{ error() }}</p> }
      @if (data(); as dash) {
        <section class="hero">
          <div>
            <span class="status"><i></i>{{ dash.alerts.length ? 'Needs attention' : 'All clear' }}</span>
            <h1>{{ greeting() }}</h1>
            <p>Monitor stock, prescriptions, and today's counter from one place.</p>
            <div class="hero-actions">
              @if (auth.has('REPORT_VIEW')) { <a routerLink="/reports">Reports</a> }
              @if (auth.has('MEDICINE_VIEW')) { <a class="ghost" routerLink="/medicines">Medicines</a> }
              @if (auth.has('SALE_CREATE')) { <a class="ghost" routerLink="/pos">Point of sale</a> }
            </div>
            <p class="meta">{{ dash.pendingPrescriptions }} prescriptions waiting · {{ dash.lowStockCount }} low stock · {{ money(dash.todaySales) }} sold today</p>
          </div>
          <svg class="capsule" viewBox="0 0 180 90" aria-hidden="true">
            <rect x="8" y="18" width="164" height="54" rx="27" fill="none" stroke="white" stroke-width="3" opacity="0.55"/>
            <path d="M90 18 v54" stroke="white" stroke-width="3" opacity="0.35"/>
          </svg>
        </section>
        <div class="kpi-row">
          <a class="kpi" routerLink="/sales"><span>Today's sales</span><strong>{{ money(dash.todaySales) }}</strong><small>Completed today</small></a>
          <a class="kpi" routerLink="/medicines"><span>Medicines</span><strong>{{ dash.medicineCount }}</strong><small>In the catalog</small></a>
          <a class="kpi" routerLink="/prescriptions"><span>Prescriptions</span><strong>{{ dash.pendingPrescriptions }}</strong><small>Waiting for review</small></a>
          <a class="kpi warn" routerLink="/stock"><span>Low stock</span><strong>{{ dash.lowStockCount }}</strong><small>At or below reorder</small></a>
          <a class="kpi bad" routerLink="/stock"><span>Expiring soon</span><strong>{{ dash.nearExpiryCount }}</strong><small>Inside the warning window</small></a>
          <a class="kpi sale" routerLink="/sales"><span>Stock value</span><strong>{{ money(dash.sellableStockValue) }}</strong><small>Sellable on hand</small></a>
        </div>
        <div class="split">
          <div class="panel">
            <h2>Sales<small>Completed sales by day</small></h2>
            @if (linePath()) {
              <svg class="area" viewBox="0 0 600 180" role="img" aria-label="Sales by day">
                <defs>
                  <linearGradient id="salesFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#12a37a" stop-opacity="0.35"/>
                    <stop offset="100%" stop-color="#12a37a" stop-opacity="0"/>
                  </linearGradient>
                </defs>
                <path [attr.d]="areaPath()" fill="url(#salesFill)"/>
                <path [attr.d]="linePath()" fill="none" stroke="#0f7a62" stroke-width="3" stroke-linejoin="round"/>
              </svg>
            } @else { <div class="empty"><strong>No daily sales chart</strong><p>Figures appear here when sales reports are available.</p></div> }
          </div>
          <div class="panel">
            <h2>Important alerts</h2>
            <div class="alert-list" style="padding: 12px">
              @for (alert of dash.alerts; track alert.message) {
                <div class="alert-item" [class]="alert.severity">{{ alert.message }}</div>
              } @empty { <div class="empty"><strong>All clear</strong><p>No low stock, expiry or prescription alerts.</p></div> }
            </div>
          </div>
        </div>
        <div class="split tables">
          <div class="panel">
            <h2>Recent sales</h2>
            <table class="data">
              <tr><th>Reference</th><th>Total</th><th>Cashier</th><th>When</th></tr>
              @for (sale of dash.recentSales; track sale.id) {
                <tr>
                  <td>@if (auth.has('SALE_VIEW')) { <a [routerLink]="['/sales', sale.id]">{{ sale.reference }}</a> } @else { {{ sale.reference }} }</td>
                  <td>{{ money(sale.totalAmount) }}</td><td>{{ sale.cashier }}</td><td>{{ when(sale.createdAt) }}</td>
                </tr>
              } @empty { <tr><td colspan="4" class="empty"><strong>No sales yet</strong></td></tr> }
            </table>
          </div>
          <div class="panel">
            <h2>Recent purchases</h2>
            <table class="data">
              <tr><th>Reference</th><th>Supplier</th><th>Total</th><th>Status</th></tr>
              @for (purchase of dash.recentPurchases; track purchase.id) {
                <tr>
                  <td>@if (auth.has('PURCHASE_VIEW')) { <a [routerLink]="['/purchases', purchase.id]">{{ purchase.reference }}</a> } @else { {{ purchase.reference }} }</td>
                  <td>{{ purchase.supplierName }}</td><td>{{ money(purchase.totalAmount) }}</td>
                  <td><span class="badge" [class]="statusClass(purchase.status)">{{ statusLabel(purchase.status) }}</span></td>
                </tr>
              } @empty { <tr><td colspan="4" class="empty"><strong>No purchases yet</strong></td></tr> }
            </table>
          </div>
        </div>
        @if (watch().length) {
          <div class="panel">
            <h2>Stock to watch</h2>
            <table class="data">
              <tr><th>Medicine</th><th>Sellable</th><th>Location</th><th>Signal</th></tr>
              @for (row of watch(); track row.medicineId + row.name) {
                <tr>
                  <td><strong>{{ row.name }}</strong> {{ row.strength }}</td>
                  <td>{{ row.sellableQuantity }} {{ row.unit }}</td>
                  <td>{{ row.locationPath || 'Unassigned' }}</td>
                  <td>
                    @if (row.sellableQuantity === 0) { <span class="badge bad">Out of stock</span> }
                    @else if (row.sellableQuantity <= row.reorderLevel) { <span class="badge warn">Low stock</span> }
                    @if (row.nearExpiry) { <span class="badge warn">Near expiry</span> }
                    @if (row.expiredQuantity > 0) { <span class="badge bad">Expired</span> }
                  </td>
                </tr>
              }
            </table>
          </div>
        }
      }
    </section>
  `,
  styles: [`
    .hero { position: relative; overflow: hidden; display: flex; justify-content: space-between; gap: 16px; padding: 22px 24px; border-radius: 18px; color: #f4fbf8; background: radial-gradient(circle at 88% 20%, rgba(255,255,255,.18), transparent 28%), linear-gradient(100deg, #0c8f72 0%, #0b6b58 48%, #127a86 100%); }
    .status { display: inline-flex; align-items: center; gap: 6px; background: rgba(255,255,255,.16); border-radius: 999px; padding: 4px 10px; font-size: 0.75rem; font-weight: 650; }
    .status i { width: 7px; height: 7px; border-radius: 50%; background: #b6f3cf; }
    .hero h1 { margin: 10px 0 0; font-size: 1.55rem; letter-spacing: -0.04em; }
    .hero p { margin: 6px 0 0; max-width: 52ch; color: #e5f6f1; }
    .hero-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
    .hero-actions a { background: #fff; color: #0d5c4c; border-radius: 10px; padding: 8px 12px; text-decoration: none; }
    .hero-actions a.ghost { background: transparent; color: #fff; border: 1px solid rgba(255,255,255,.45); }
    .meta { margin-top: 14px; font-size: 0.8rem; color: #d7f3eb; }
    .capsule { width: 180px; flex: none; align-self: center; }
    .kpi-row { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
    .kpi { background: #fff; border: 1px solid #e7eeeb; border-radius: 16px; padding: 12px 14px; text-decoration: none; color: inherit; box-shadow: 0 8px 24px rgba(18, 40, 34, 0.04); border-top: 3px solid #12a37a; }
    .kpi.warn { border-top-color: #e39b2d; }
    .kpi.bad { border-top-color: #e06a62; }
    .kpi.sale { border-top-color: #3aa0b5; }
    .kpi span { display: block; color: #6d807b; font-size: 0.72rem; font-weight: 700; }
    .kpi strong { display: block; margin-top: 6px; font-size: 1.15rem; letter-spacing: -0.04em; }
    .kpi small { display: block; margin-top: 4px; color: #8b9a96; font-size: 0.72rem; }
    .kpi:hover { text-decoration: none; border-color: #cfe3db; }
    .split { display: grid; grid-template-columns: 1.4fr .8fr; gap: 14px; }
    .split.tables { grid-template-columns: 1fr 1fr; }
    .split table.data { min-width: 0; }
    .split td:first-child { white-space: nowrap; }
    .area { width: 100%; height: 180px; display: block; }
    .empty { padding: 28px 16px 32px; }
    @media (max-width: 1279px) { .split, .kpi-row { grid-template-columns: 1fr 1fr; } .capsule { display: none; } }
    @media (max-width: 800px) { .kpi-row { grid-template-columns: 1fr; } }
  `]
})
export class DashboardComponent {
  private readonly pharmacy = inject(PharmacyService);
  readonly auth = inject(AuthService);
  readonly data = signal<Dashboard | null>(null);
  readonly chart = signal<{ label: string; amount: number; height: number }[]>([]);
  readonly watch = signal<StockRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  currency = 'USD';
  money = (value: number) => formatMoney(value, this.currency);
  readonly when = when;

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    if (!this.auth.has('DASHBOARD_VIEW')) {
      this.loading.set(false);
      this.error.set('Your role can sign in, but it does not include the dashboard. Use the menu for the tasks assigned to you.');
      return;
    }
    this.pharmacy.dashboard().subscribe({
      next: data => { this.data.set(data); this.loading.set(false); },
      error: error => { this.error.set(errorMessage(error)); this.loading.set(false); }
    });
    if (this.auth.has('REPORT_VIEW')) {
      this.pharmacy.report('/reports/sales', { granularity: 'day' }).subscribe({
        next: table => this.chart.set(this.bars(table)),
        error: () => this.chart.set([])
      });
    }
    if (this.auth.has('STOCK_VIEW')) {
      this.pharmacy.stock({ status: 'LOW', page: 0, size: 5 }).subscribe({
        next: low => this.pharmacy.stock({ status: 'NEAR', page: 0, size: 5 }).subscribe({
          next: near => {
            const merged = [...low.content];
            for (const row of near.content) {
              if (!merged.some(item => item.medicineId === row.medicineId)) { merged.push(row); }
            }
            this.watch.set(merged.slice(0, 8));
          }
        })
      });
    }
  }

  greeting(): string {
    const hour = new Date().getHours();
    const hello = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
    const name = (this.auth.user()?.fullName ?? '').split(' ')[0];
    return name ? `${hello}, ${name}` : hello;
  }

  linePath(): string {
    return this.points().map((point, index) => `${index ? 'L' : 'M'}${point.x},${point.y}`).join(' ');
  }

  areaPath(): string {
    const points = this.points();
    if (points.length < 2) { return ''; }
    const last = points[points.length - 1];
    const first = points[0];
    return `${this.linePath()} L${last.x},168 L${first.x},168 Z`;
  }

  private recentDays(): { label: string; amount: number; height: number }[] {
    const sales = this.data()?.recentSales ?? [];
    const totals = new Map<string, number>();
    for (const sale of sales) {
      const day = (sale.createdAt || '').slice(0, 10);
      totals.set(day, (totals.get(day) ?? 0) + Number(sale.totalAmount || 0));
    }
    return [...totals.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([day, amount]) => ({
      label: day.slice(5), amount, height: 0
    }));
  }

  private points(): { x: number; y: number }[] {
    const rows: { amount: number }[] = this.chart().length ? this.chart() : this.recentDays();
    if (!rows.length) { return []; }
    const max = Math.max(...rows.map(row => row.amount), 1);
    const step = rows.length === 1 ? 0 : 560 / (rows.length - 1);
    return rows.map((row, index) => ({ x: 20 + index * step, y: 150 - (row.amount / max) * 120 }));
  }

  statusLabel = statusLabel;
  statusClass(status: string): string {
    if (status === 'CONFIRMED' || status === 'PAID') { return 'ok'; }
    if (status === 'CANCELLED') { return 'bad'; }
    return 'warn';
  }

  private bars(table: ReportTable): { label: string; amount: number; height: number }[] {
    const rows = table.rows.slice(-10);
    const max = Math.max(...rows.map(row => Number(row['totalAmount'] || 0)), 1);
    return rows.map(row => ({
      label: (row['period'] || '').slice(5),
      amount: Number(row['totalAmount'] || 0),
      height: Math.max(6, (Number(row['totalAmount'] || 0) / max) * 100)
    }));
  }
}

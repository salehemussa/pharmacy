import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { PopoverModule } from 'primeng/popover';
import { AuthService } from '../core/auth.service';
import { PharmacyService } from '../core/pharmacy.service';
import { AlertItem, DisplayProfile } from '../core/models';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  group: string;
  permissions: string[];
}

const NAV: NavItem[] = [
  { label: 'Dashboard', path: '/dashboard', icon: 'pi pi-th-large', group: 'Overview', permissions: ['DASHBOARD_VIEW'] },
  { label: 'Point of sale', path: '/pos', icon: 'pi pi-shopping-cart', group: 'Counter', permissions: ['SALE_CREATE'] },
  { label: 'Sales', path: '/sales', icon: 'pi pi-receipt', group: 'Counter', permissions: ['SALE_VIEW'] },
  { label: 'Returns', path: '/returns', icon: 'pi pi-undo', group: 'Counter', permissions: ['RETURN_VIEW'] },
  { label: 'Prescriptions', path: '/prescriptions', icon: 'pi pi-file', group: 'Care', permissions: ['PRESCRIPTION_VIEW'] },
  { label: 'Customers', path: '/customers', icon: 'pi pi-users', group: 'Care', permissions: ['CUSTOMER_VIEW'] },
  { label: 'Medicines', path: '/medicines', icon: 'pi pi-box', group: 'Inventory', permissions: ['MEDICINE_VIEW'] },
  { label: 'Stock', path: '/stock', icon: 'pi pi-warehouse', group: 'Inventory', permissions: ['STOCK_VIEW'] },
  { label: 'Locations', path: '/locations', icon: 'pi pi-map-marker', group: 'Inventory', permissions: ['LOCATION_VIEW'] },
  { label: 'Purchases', path: '/purchases', icon: 'pi pi-shopping-bag', group: 'Supply', permissions: ['PURCHASE_VIEW'] },
  { label: 'Suppliers', path: '/suppliers', icon: 'pi pi-truck', group: 'Supply', permissions: ['SUPPLIER_VIEW'] },
  { label: 'Reports', path: '/reports', icon: 'pi pi-chart-bar', group: 'Management', permissions: ['REPORT_VIEW'] },
  { label: 'Activity', path: '/audit', icon: 'pi pi-history', group: 'Management', permissions: ['AUDIT_VIEW'] },
  { label: 'Users', path: '/users', icon: 'pi pi-id-card', group: 'Administration', permissions: ['USER_VIEW'] },
  { label: 'Roles', path: '/roles', icon: 'pi pi-shield', group: 'Administration', permissions: ['ROLE_MANAGE'] },
  { label: 'Settings', path: '/settings', icon: 'pi pi-cog', group: 'Administration', permissions: ['SETTINGS_MANAGE'] }
];

const TITLES: Record<string, string> = {
  dashboard: 'Dashboard', pos: 'Point of sale', sales: 'Sales', returns: 'Returns',
  prescriptions: 'Prescriptions', customers: 'Customers', medicines: 'Medicines', stock: 'Stock',
  locations: 'Locations', purchases: 'Purchases', suppliers: 'Suppliers', reports: 'Reports',
  audit: 'Activity', users: 'Users', roles: 'Roles', settings: 'Settings', profile: 'Profile'
};

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonModule, PopoverModule],
  template: `
    <div class="shell">
      @if (mobile() && menuOpen()) { <button class="backdrop" type="button" aria-label="Close menu" (click)="menuOpen.set(false)"></button> }
      <aside class="nav" [class.menu-open]="menuOpen()" [class.slim]="collapsed() && !mobile()">
        <div class="brand">
          <img class="logo" src="logo-dark.png" alt="Hangou Memorial Hospital" />
        </div>
        <nav>
          @for (group of groups(); track group.label) {
            @if (!collapsed() || mobile()) { <p>{{ group.label }}</p> }
            @for (item of group.items; track item.path) {
              <a [routerLink]="item.path" routerLinkActive="active" [title]="collapsed() && !mobile() ? item.label : ''" (click)="menuOpen.set(false)">
                <i [class]="item.icon"></i>
                @if (!collapsed() || mobile()) { <span>{{ item.label }}</span> }
              </a>
            }
          }
        </nav>
        @if (!mobile()) {
          <button class="collapse" type="button" (click)="toggle()" [attr.aria-label]="collapsed() ? 'Expand menu' : 'Collapse menu'">
            <i [class]="collapsed() ? 'pi pi-chevron-right' : 'pi pi-chevron-left'"></i>
          </button>
        }
      </aside>
      <div class="workspace">
        <header class="top">
          @if (mobile()) {
            <p-button icon="pi pi-bars" [text]="true" [rounded]="true" (onClick)="menuOpen.set(!menuOpen())" ariaLabel="Open menu" />
          }
          <div class="crumb">
            <strong>{{ title() }}</strong>
          </div>
          <form class="search" (submit)="find($event)">
            <i class="pi pi-search"></i>
            <input name="q" type="search" placeholder="Search medicines, patients, orders" aria-label="Search" />
          </form>
          <div class="tools">
          @if (auth.has('ALERT_VIEW')) {
            <p-button icon="pi pi-bell" [text]="true" [rounded]="true" [badge]="alerts().length ? '' + alerts().length : undefined" badgeSeverity="danger" (onClick)="alertsPanel.toggle($event)" ariaLabel="Notifications" />
            <p-popover #alertsPanel>
              @for (alert of alerts(); track alert.message) {
                <button class="alert-link" type="button" (click)="openAlert(alert); alertsPanel.hide()">{{ alert.message }}</button>
              } @empty { <p class="muted">No alerts</p> }
            </p-popover>
          }
          <button class="profile" type="button" (click)="userPanel.toggle($event)">
            <span class="avatar">{{ initials() }}</span>
            <span>
              <strong>{{ auth.user()?.fullName }}</strong>
              <small>{{ roleLabel() }}</small>
            </span>
          </button>
          <p-popover #userPanel>
            <button class="alert-link" type="button" (click)="openProfile(userPanel)"><i class="pi pi-user"></i> Profile</button>
            <button class="alert-link" type="button" (click)="logout()"><i class="pi pi-sign-out"></i> Sign out</button>
          </p-popover>
          </div>
        </header>
        <main><router-outlet /></main>
      </div>
    </div>
  `,
  styles: [`
    .shell { display: flex; min-height: 100vh; background: #f4f7f6; }
    .nav { width: 236px; background: #fff; color: #24312e; display: flex; flex-direction: column; position: sticky; top: 0; height: 100vh; flex: none; border-right: 1px solid #e7eeeb; }
    .nav.slim { width: 72px; }
    .brand { display: flex; align-items: center; padding: 16px 16px 8px; min-height: 64px; }
    .logo { height: 42px; width: auto; max-width: 196px; object-fit: contain; object-position: left center; display: block; }
    .nav.slim .brand { justify-content: center; padding: 16px 8px; }
    .nav.slim .logo { width: 36px; height: 36px; max-width: none; object-fit: cover; object-position: left center; }
    nav { padding: 8px 12px 64px; overflow: auto; }
    nav p { margin: 14px 10px 6px; color: #8b9a96; font-size: 0.66rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; }
    nav a { display: flex; align-items: center; gap: 10px; color: #4d5e5a; text-decoration: none; border-radius: 10px; padding: 8px 10px; margin-bottom: 2px; font-size: 0.86rem; font-weight: 600; }
    nav a i { font-size: 0.95rem; width: 1.1rem; text-align: center; color: #6d807b; }
    nav a:hover { background: #f3f7f5; color: #16312c; }
    nav a.active { background: #0f7a62; color: #fff; }
    nav a.active i { color: #fff; }
    .nav.slim .brand { justify-content: center; }
    .nav.slim nav a { justify-content: center; }
    .collapse { position: absolute; left: 12px; right: 12px; bottom: 12px; border: 1px solid #e7eeeb; border-radius: 10px; background: #f7faf9; color: #3d524d; height: 32px; cursor: pointer; }
    .collapse:hover { background: #eef5f2; }
    .nav { position: sticky; }
    .workspace { flex: 1; min-width: 0; }
    .top { position: sticky; top: 0; z-index: 3; height: 64px; display: flex; align-items: center; gap: 12px; padding: 0 20px; background: rgba(244,247,246,.92); }
    .crumb { display: flex; align-items: center; min-width: 120px; }
    .crumb strong { color: #172421; font-weight: 700; font-size: 1.15rem; letter-spacing: -0.03em; }
    .search { flex: 1; max-width: 420px; display: flex; align-items: center; gap: 8px; height: 38px; padding: 0 14px; background: #fff; border: 1px solid #e6eeea; border-radius: 999px; color: #8b9a96; }
    .search input { flex: 1; border: 0; outline: 0; background: transparent; font: inherit; color: #172421; min-width: 0; }
    .search input::placeholder { color: #8b9a96; }
    .tools { margin-left: auto; display: flex; align-items: center; gap: 4px; }
    .profile { display: flex; align-items: center; gap: 8px; border: 0; background: transparent; border-radius: 999px; padding: 4px 8px 4px 4px; cursor: pointer; }
    .profile:hover { background: #fff; }
    .profile strong, .profile small { display: block; text-align: left; }
    .profile strong { font-size: 0.84rem; color: #172421; }
    .profile small { color: #6d807b; text-transform: capitalize; font-size: 0.7rem; }
    .avatar { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: #0f7a62; color: #fff; font-weight: 700; font-size: 0.75rem; }
    main { padding: 4px 20px 28px; }
    .alert-link { display: flex; gap: 8px; width: 100%; text-align: left; border: 0; background: transparent; padding: 8px 4px; cursor: pointer; color: #14211e; }
    .muted { color: #667872; margin: 0; }
    .backdrop { position: fixed; inset: 0; border: 0; background: rgba(16,36,32,.4); z-index: 4; }
    @media (max-width: 960px) {
      .nav { position: fixed; z-index: 5; transform: translateX(-105%); transition: transform .2s ease; }
      .nav.menu-open { transform: none; }
    }
    @media (max-width: 800px) { .profile span:last-child, .search { display: none; } main { padding: 8px 12px 32px; } }
  `]
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  private readonly pharmacy = inject(PharmacyService);
  private readonly router = inject(Router);
  readonly menuOpen = signal(false);
  readonly mobile = signal(window.matchMedia('(max-width: 960px)').matches);
  readonly collapsed = signal(localStorage.getItem('pharmacy.nav') === 'slim');
  readonly profile = signal<DisplayProfile | null>(null);
  readonly alerts = signal<AlertItem[]>([]);
  readonly title = signal('Dashboard');
  readonly groups = signal<{ label: string; items: NavItem[] }[]>([]);

  constructor() {
    const narrow = window.matchMedia('(max-width: 960px)');
    const syncNav = () => this.mobile.set(narrow.matches);
    narrow.addEventListener('change', syncNav);
    window.addEventListener('resize', syncNav);
    const allowed = NAV.filter(item => this.auth.has(...item.permissions));
    this.groups.set([...new Set(allowed.map(item => item.group))].map(label => ({ label, items: allowed.filter(item => item.group === label) })));
    this.pharmacy.display().subscribe({ next: profile => this.profile.set(profile), error: () => undefined });
    this.loadAlerts();
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => this.syncTitle());
    this.syncTitle();
  }

  toggle(): void {
    this.collapsed.update(value => !value);
    localStorage.setItem('pharmacy.nav', this.collapsed() ? 'slim' : 'wide');
  }

  initials(): string {
    return (this.auth.user()?.fullName ?? 'U').split(' ').slice(0, 2).map(part => part[0]).join('').toUpperCase();
  }

  roleLabel(): string {
    return (this.auth.user()?.roles ?? []).map(role => role.toLowerCase().replaceAll('_', ' ')).join(', ');
  }

  openAlert(alert: AlertItem): void {
    const path = alert.type.includes('PRESCRIPTION') ? '/prescriptions' : '/stock';
    void this.router.navigate([path]);
  }

  find(event: Event): void {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    const q = String(new FormData(form).get('q') ?? '').trim();
    const queryParams = q ? { q } : {};
    if (this.auth.has('MEDICINE_VIEW')) { void this.router.navigate(['/medicines'], { queryParams }); return; }
    if (this.auth.has('CUSTOMER_VIEW')) { void this.router.navigate(['/customers'], { queryParams }); return; }
    if (this.auth.has('SALE_VIEW')) { void this.router.navigate(['/sales'], { queryParams }); }
  }

  openProfile(panel: { hide: () => void }): void {
    panel.hide();
    void this.router.navigate(['/profile']);
  }

  logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigate(['/login']));
  }

  private syncTitle(): void {
    const segment = this.router.url.split('?')[0].split('/').filter(Boolean)[0] ?? 'dashboard';
    this.title.set(TITLES[segment] ?? 'Pharmacy');
  }

  private loadAlerts(): void {
    if (!this.auth.has('ALERT_VIEW')) { return; }
    this.pharmacy.alerts().subscribe({ next: items => this.alerts.set(items), error: () => this.alerts.set([]) });
  }
}

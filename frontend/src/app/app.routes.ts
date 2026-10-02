import { Routes } from '@angular/router';
import { authGuard, changePasswordGuard, guestGuard, passwordGuard, permissionGuard } from './core/auth.guard';
import { ShellComponent } from './layout/shell.component';
import { LoginComponent } from './pages/login.component';
import { ChangePasswordComponent } from './pages/change-password.component';
import { DashboardComponent } from './pages/dashboard.component';
import { MedicinesComponent } from './pages/medicines.component';
import { LocationsComponent } from './pages/locations.component';
import { StockComponent } from './pages/stock.component';
import { SuppliersComponent } from './pages/suppliers.component';
import { PurchasesComponent } from './pages/purchases.component';
import { PurchaseFormComponent } from './pages/purchase-form.component';
import { CustomersComponent, CustomerHistoryComponent } from './pages/customers.component';
import { PrescriptionsComponent } from './pages/prescriptions.component';
import { PrescriptionFormComponent } from './pages/prescription-form.component';
import { PosComponent } from './pages/pos.component';
import { SaleDetailComponent, SalesComponent } from './pages/sales.component';
import { ReturnsComponent } from './pages/returns.component';
import { ReportsComponent } from './pages/reports.component';
import { UsersComponent } from './pages/users.component';
import { RolesComponent } from './pages/roles.component';
import { AuditComponent } from './pages/audit.component';
import { SettingsComponent } from './pages/settings.component';
import { ProfileComponent } from './pages/profile.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'change-password', component: ChangePasswordComponent, canActivate: [changePasswordGuard] },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: DashboardComponent, canActivate: [permissionGuard] },
      { path: 'pos', component: PosComponent, canActivate: [permissionGuard], data: { anyOf: ['SALE_CREATE'] } },
      { path: 'sales', component: SalesComponent, canActivate: [permissionGuard], data: { anyOf: ['SALE_VIEW'] } },
      { path: 'sales/:id', component: SaleDetailComponent, canActivate: [permissionGuard], data: { anyOf: ['SALE_VIEW'] } },
      { path: 'returns', component: ReturnsComponent, canActivate: [permissionGuard], data: { anyOf: ['RETURN_VIEW'] } },
      { path: 'prescriptions', component: PrescriptionsComponent, canActivate: [permissionGuard], data: { anyOf: ['PRESCRIPTION_VIEW'] } },
      { path: 'prescriptions/new', component: PrescriptionFormComponent, canActivate: [permissionGuard], data: { anyOf: ['PRESCRIPTION_MANAGE'] } },
      { path: 'prescriptions/:id', component: PrescriptionFormComponent, canActivate: [permissionGuard], data: { anyOf: ['PRESCRIPTION_VIEW'] } },
      { path: 'customers', component: CustomersComponent, canActivate: [permissionGuard], data: { anyOf: ['CUSTOMER_VIEW'] } },
      { path: 'customers/:id', component: CustomerHistoryComponent, canActivate: [permissionGuard], data: { anyOf: ['CUSTOMER_VIEW'] } },
      { path: 'medicines', component: MedicinesComponent, canActivate: [permissionGuard], data: { anyOf: ['MEDICINE_VIEW'] } },
      { path: 'locations', component: LocationsComponent, canActivate: [permissionGuard], data: { anyOf: ['LOCATION_VIEW'] } },
      { path: 'stock', component: StockComponent, canActivate: [permissionGuard], data: { anyOf: ['STOCK_VIEW'] } },
      { path: 'suppliers', component: SuppliersComponent, canActivate: [permissionGuard], data: { anyOf: ['SUPPLIER_VIEW'] } },
      { path: 'purchases', component: PurchasesComponent, canActivate: [permissionGuard], data: { anyOf: ['PURCHASE_VIEW'] } },
      { path: 'purchases/new', component: PurchaseFormComponent, canActivate: [permissionGuard], data: { anyOf: ['PURCHASE_MANAGE'] } },
      { path: 'purchases/:id', component: PurchaseFormComponent, canActivate: [permissionGuard], data: { anyOf: ['PURCHASE_VIEW'] } },
      { path: 'reports', component: ReportsComponent, canActivate: [permissionGuard], data: { anyOf: ['REPORT_VIEW'] } },
      { path: 'users', component: UsersComponent, canActivate: [permissionGuard], data: { anyOf: ['USER_VIEW'] } },
      { path: 'roles', component: RolesComponent, canActivate: [permissionGuard], data: { anyOf: ['ROLE_MANAGE'] } },
      { path: 'audit', component: AuditComponent, canActivate: [permissionGuard], data: { anyOf: ['AUDIT_VIEW'] } },
      { path: 'settings', component: SettingsComponent, canActivate: [permissionGuard], data: { anyOf: ['SETTINGS_MANAGE'] } },
      { path: 'profile', component: ProfileComponent, canActivate: [passwordGuard] }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];

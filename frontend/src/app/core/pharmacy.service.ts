import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import {
  AuditLog, Availability, Batch, Category, Customer, Dashboard, Dispensing, DisplayProfile, HistoryItem, AlertItem,
  Location, ManagementSummary, Medicine, Movement, Named, Page, PaymentMethod, Permission, Prescription,
  PrescriptionSummary, Purchase, PurchaseSummary, Receipt, ReportTable, Role, Sale, SaleReturn, SaleSummary,
  Setting, StockRow, Supplier, UserAccount
} from './models';

type Query = Record<string, string | number | boolean | null | undefined>;

@Injectable({ providedIn: 'root' })
export class PharmacyService {
  private readonly api = inject(ApiService);

  display(): Observable<DisplayProfile> {
    return this.api.get('/settings/display');
  }

  medicines(query: Query): Observable<Page<Medicine>> { return this.api.get('/medicines', query); }
  medicine(id: number): Observable<Medicine> { return this.api.get(`/medicines/${id}`); }
  saveMedicine(id: number | null, body: unknown): Observable<Medicine> {
    return id ? this.api.put(`/medicines/${id}`, body) : this.api.post('/medicines', body);
  }
  categories(): Observable<Category[]> { return this.api.get('/categories'); }
  saveCategory(id: number | null, body: unknown): Observable<Category> {
    return id ? this.api.put(`/categories/${id}`, body) : this.api.post('/categories', body);
  }
  locations(active?: boolean): Observable<Location[]> { return this.api.get('/locations', { active }); }
  saveLocation(id: number | null, body: unknown): Observable<Location> {
    return id ? this.api.put(`/locations/${id}`, body) : this.api.post('/locations', body);
  }
  dosageForms(active?: boolean): Observable<Named[]> { return this.api.get('/dosage-forms', { active }); }
  saveDosageForm(id: number | null, body: unknown): Observable<Named> {
    return id ? this.api.put(`/dosage-forms/${id}`, body) : this.api.post('/dosage-forms', body);
  }
  units(active?: boolean): Observable<Named[]> { return this.api.get('/units', { active }); }
  saveUnit(id: number | null, body: unknown): Observable<Named> {
    return id ? this.api.put(`/units/${id}`, body) : this.api.post('/units', body);
  }

  stock(query: Query): Observable<Page<StockRow>> { return this.api.get('/stock', query); }
  availability(id: number): Observable<Availability[]> { return this.api.get(`/medicines/${id}/availability`); }
  batches(query: Query): Observable<Page<Batch>> { return this.api.get('/batches', query); }
  movements(query: Query): Observable<Page<Movement>> { return this.api.get('/stock-movements', query); }
  adjust(body: unknown): Observable<Movement> { return this.api.post('/stock/adjustments', body); }

  suppliers(query: Query): Observable<Page<Supplier>> { return this.api.get('/suppliers', query); }
  saveSupplier(id: number | null, body: unknown): Observable<Supplier> {
    return id ? this.api.put(`/suppliers/${id}`, body) : this.api.post('/suppliers', body);
  }

  purchases(query: Query): Observable<Page<PurchaseSummary>> { return this.api.get('/purchases', query); }
  purchase(id: number): Observable<Purchase> { return this.api.get(`/purchases/${id}`); }
  savePurchase(id: number | null, body: unknown): Observable<Purchase> {
    return id ? this.api.put(`/purchases/${id}`, body) : this.api.post('/purchases', body);
  }
  confirmPurchase(id: number): Observable<Purchase> { return this.api.post(`/purchases/${id}/confirm`, {}); }
  cancelPurchase(id: number, reason: string): Observable<Purchase> { return this.api.post(`/purchases/${id}/cancel`, { reason }); }
  payPurchase(id: number, amountPaid: number): Observable<Purchase> { return this.api.put(`/purchases/${id}/payment`, { amountPaid }); }

  customers(query: Query): Observable<Page<Customer>> { return this.api.get('/customers', query); }
  customer(id: number): Observable<Customer> { return this.api.get(`/customers/${id}`); }
  customerHistory(id: number): Observable<{ customer: Customer; items: HistoryItem[] }> { return this.api.get(`/customers/${id}/history`); }
  saveCustomer(id: number | null, body: unknown): Observable<Customer> {
    return id ? this.api.put(`/customers/${id}`, body) : this.api.post('/customers', body);
  }

  prescriptions(query: Query): Observable<Page<PrescriptionSummary>> { return this.api.get('/prescriptions', query); }
  prescription(id: number): Observable<Prescription> { return this.api.get(`/prescriptions/${id}`); }
  savePrescription(id: number | null, body: unknown): Observable<Prescription> {
    return id ? this.api.put(`/prescriptions/${id}`, body) : this.api.post('/prescriptions', body);
  }
  reviewPrescription(id: number): Observable<Prescription> { return this.api.post(`/prescriptions/${id}/review`, {}); }
  cancelPrescription(id: number, reason: string): Observable<Prescription> { return this.api.post(`/prescriptions/${id}/cancel`, { reason }); }
  dispense(id: number, body: unknown): Observable<Dispensing> { return this.api.post(`/prescriptions/${id}/dispense`, body); }
  dispensings(query: Query): Observable<Page<Dispensing>> { return this.api.get('/dispensings', query); }

  pos(q: string): Observable<StockRow[]> { return this.api.get('/pos/medicines', { q }); }
  sales(query: Query): Observable<Page<SaleSummary>> { return this.api.get('/sales', query); }
  sale(id: number): Observable<Sale> { return this.api.get(`/sales/${id}`); }
  completeSale(body: unknown): Observable<Sale> { return this.api.post('/sales', body); }
  cancelSale(id: number, reason: string): Observable<Sale> { return this.api.post(`/sales/${id}/cancel`, { reason }); }
  receipt(id: number): Observable<Receipt> { return this.api.get(`/sales/${id}/receipt`); }
  paymentMethods(active?: boolean): Observable<PaymentMethod[]> { return this.api.get('/payment-methods', { active }); }
  savePaymentMethod(id: number | null, body: unknown): Observable<PaymentMethod> {
    return id ? this.api.put(`/payment-methods/${id}`, body) : this.api.post('/payment-methods', body);
  }

  returns(query: Query): Observable<Page<SaleReturn>> { return this.api.get('/returns', query); }
  returnOne(id: number): Observable<SaleReturn> { return this.api.get(`/returns/${id}`); }
  createReturn(body: unknown): Observable<SaleReturn> { return this.api.post('/returns', body); }
  approveReturn(id: number): Observable<SaleReturn> { return this.api.post(`/returns/${id}/approve`, {}); }
  rejectReturn(id: number, reason: string): Observable<SaleReturn> { return this.api.post(`/returns/${id}/reject`, { reason }); }

  dashboard(): Observable<Dashboard> { return this.api.get('/dashboard'); }
  alerts(): Observable<AlertItem[]> { return this.api.get('/alerts'); }
  report(path: string, query: Query): Observable<ReportTable> { return this.api.get(path, query); }
  management(query: Query): Observable<ManagementSummary> { return this.api.get('/reports/management', query); }
  reportCsv(path: string, query: Query): Observable<Blob> { return this.api.blob(path, { ...query, format: 'csv' }); }

  users(query: Query): Observable<Page<UserAccount>> { return this.api.get('/users', query); }
  saveUser(body: unknown): Observable<UserAccount> { return this.api.post('/users', body); }
  updateUser(id: number, body: unknown): Observable<UserAccount> { return this.api.put(`/users/${id}`, body); }
  activateUser(id: number, active: boolean): Observable<UserAccount> { return this.api.post(`/users/${id}/activation`, { active }); }
  resetPassword(id: number): Observable<{ temporaryPassword: string }> { return this.api.post(`/users/${id}/reset-password`, {}); }
  roles(): Observable<Role[]> { return this.api.get('/roles'); }
  permissions(): Observable<Permission[]> { return this.api.get('/permissions'); }
  saveRolePermissions(id: number, permissionCodes: string[]): Observable<Role> {
    return this.api.put(`/roles/${id}/permissions`, { permissionCodes });
  }
  settings(): Observable<Setting[]> { return this.api.get('/settings'); }
  saveSettings(values: Record<string, string>): Observable<Setting[]> { return this.api.put('/settings', { values }); }
  audit(query: Query): Observable<Page<AuditLog>> { return this.api.get('/audit-logs', query); }
}

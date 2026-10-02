export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  message?: string;
  code?: string;
  fieldErrors?: Record<string, string>;
}

export interface UserProfile {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone: string | null;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserProfile;
}

export interface DisplayProfile {
  name: string;
  currency: string;
  timezone: string;
  expiryWarningDays: number;
  allowAuthorizedExpiredUse: boolean;
  maxDiscountPercent: number;
}

export interface Named {
  id: number;
  name: string;
  active: boolean;
}

export interface Category extends Named {
  description: string | null;
}

export interface Location {
  id: number;
  parentId: number | null;
  locationType: string;
  code: string;
  name: string;
  path: string;
  active: boolean;
}

export interface Medicine {
  id: number;
  name: string;
  genericName: string;
  brandName: string | null;
  categoryId: number | null;
  categoryName: string | null;
  dosageForm: string;
  strength: string;
  unit: string;
  manufacturer: string | null;
  barcode: string | null;
  reorderLevel: number;
  purchasePrice: number;
  sellingPrice: number;
  locationId: number | null;
  locationCode: string | null;
  locationPath: string | null;
  active: boolean;
}

export interface Supplier {
  id: number;
  name: string;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  active: boolean;
}

export interface StockRow {
  medicineId: number;
  name: string;
  genericName: string;
  strength: string;
  dosageForm: string;
  unit: string;
  reorderLevel: number;
  sellableQuantity: number;
  onHandQuantity: number;
  expiredQuantity: number;
  sellableValue: number;
  stockValue: number;
  locationId: number | null;
  locationPath: string | null;
  active: boolean;
  nearExpiry: boolean;
}

export interface Availability {
  batchId: number;
  batchNumber: string;
  expiryDate: string;
  manufacturingDate: string | null;
  quantityOnHand: number;
  sellingPrice: number;
  purchasePrice: number;
  sellable: boolean;
  expired: boolean;
  locationPath: string | null;
}

export interface Batch {
  id: number;
  medicineId: number;
  medicineName: string;
  batchNumber: string;
  manufacturingDate: string | null;
  expiryDate: string;
  quantityReceived: number;
  quantityOnHand: number;
  purchasePrice: number;
  sellingPrice: number;
  supplierId: number | null;
  supplierName: string | null;
  locationId: number | null;
  locationPath: string | null;
}

export interface Movement {
  id: number;
  medicineId: number;
  medicineName: string;
  batchId: number;
  batchNumber: string;
  movementType: string;
  quantity: number;
  direction: string;
  balanceAfter: number;
  referenceType: string;
  referenceId: number;
  reason: string | null;
  performedBy: string;
  createdAt: string;
}

export interface PurchaseItem {
  id?: number;
  medicineId: number;
  medicineName?: string;
  quantity: number;
  purchasePrice: number;
  sellingPrice: number;
  batchNumber: string;
  manufacturingDate: string | null;
  expiryDate: string;
  locationId: number | null;
  locationCode?: string | null;
  lineTotal?: number;
}

export interface Purchase {
  id: number;
  reference: string;
  supplierId: number;
  supplierName: string;
  purchaseDate: string;
  status: string;
  paymentStatus: string;
  totalAmount: number;
  amountPaid: number;
  notes: string | null;
  createdBy: string;
  confirmedBy: string | null;
  confirmedAt: string | null;
  cancellationReason: string | null;
  items: PurchaseItem[];
}

export interface PurchaseSummary {
  id: number;
  reference: string;
  supplierId: number;
  supplierName: string;
  purchaseDate: string;
  status: string;
  paymentStatus: string;
  totalAmount: number;
  amountPaid: number;
}

export interface Customer {
  id: number;
  reference: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  dateOfBirth: string | null;
  notes: string | null;
  active: boolean;
}

export interface HistoryItem {
  type: string;
  id: number;
  reference: string;
  occurredAt: string;
  status: string;
}

export interface PrescriptionItem {
  id?: number;
  medicineId: number;
  medicineName?: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  quantityDispensed?: number;
  instructions: string | null;
}

export interface Prescription {
  id: number;
  reference: string;
  customerId: number;
  customerName: string;
  prescriptionDate: string;
  prescriberName: string | null;
  prescriberLicense: string | null;
  status: string;
  notes: string | null;
  createdBy: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  cancellationReason: string | null;
  items: PrescriptionItem[];
}

export interface PrescriptionSummary {
  id: number;
  reference: string;
  customerId: number;
  customerName: string;
  prescriptionDate: string;
  status: string;
  prescriberName: string | null;
}

export interface Dispensing {
  id: number;
  reference: string;
  prescriptionId: number;
  prescriptionReference: string;
  dispensedBy: string;
  dispensedAt: string;
  notes: string | null;
  expiredAuthorized: boolean;
  expiredReason: string | null;
  items: { prescriptionItemId: number; medicineId: number; medicineName: string; batchId: number; batchNumber: string; expiryDate: string; quantity: number }[];
}

export interface SaleItem {
  id: number;
  medicineId: number;
  medicineName: string;
  batchId: number;
  batchNumber: string;
  expiryDate: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  lineTotal: number;
  quantityReturned: number;
}

export interface Payment {
  id: number;
  methodCode: string;
  methodName: string;
  amount: number;
  tenderedAmount: number | null;
  changeAmount: number | null;
  reference: string | null;
  paidAt: string;
  receivedBy: string;
}

export interface Sale {
  id: number;
  reference: string;
  customerId: number | null;
  customerName: string | null;
  status: string;
  subtotal: number;
  discountAmount: number;
  totalAmount: number;
  notes: string | null;
  cashier: string;
  createdAt: string;
  cancellationReason: string | null;
  expiredAuthorized: boolean;
  items: SaleItem[];
  payments: Payment[];
}

export interface SaleSummary {
  id: number;
  reference: string;
  customerId: number | null;
  customerName: string | null;
  status: string;
  totalAmount: number;
  cashier: string;
  createdAt: string;
}

export interface PaymentMethod {
  id: number;
  code: string;
  name: string;
  active: boolean;
}

export interface Receipt {
  pharmacyName: string;
  address: string;
  phone: string;
  currency: string;
  footer: string;
  sale: Sale;
}

export interface SaleReturn {
  id: number;
  reference: string;
  saleId: number;
  saleReference: string;
  status: string;
  reason: string;
  refundAmount: number;
  refundMethod: string | null;
  refundReference: string | null;
  createdBy: string;
  createdAt: string;
  approvedBy: string | null;
  rejectionReason: string | null;
  items: { saleItemId: number; medicineId: number; medicineName: string; batchNumber: string; quantity: number; refundAmount: number }[];
}

export interface UserAccount {
  id: number;
  username: string;
  email: string;
  fullName: string;
  phone: string | null;
  active: boolean;
  mustChangePassword: boolean;
  roles: string[];
}

export interface Role {
  id: number;
  name: string;
  description: string;
  permissions: string[];
}

export interface Permission {
  id: number;
  code: string;
  description: string;
  module: string;
}

export interface Setting {
  key: string;
  value: string;
  description: string;
}

export interface AuditLog {
  id: number;
  userId: number | null;
  username: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: Record<string, unknown> | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AlertItem {
  type: string;
  severity: string;
  message: string;
}

export interface Dashboard {
  todaySales: number;
  totalSales: number;
  sellableStockValue: number;
  medicineCount: number;
  lowStockCount: number;
  outOfStockCount: number;
  medicinesWithExpiredStock: number;
  expiredQuantity: number;
  nearExpiryCount: number;
  pendingPrescriptions: number;
  recentSales: { id: number; reference: string; totalAmount: number; cashier: string; createdAt: string }[];
  recentPurchases: { id: number; reference: string; supplierName: string; totalAmount: number; status: string; purchaseDate: string }[];
  alerts: AlertItem[];
}

export interface ReportTable {
  columns: string[];
  rows: Record<string, string>[];
}

export interface ManagementSummary {
  from: string;
  to: string;
  grossSales: number;
  approvedReturns: number;
  netSales: number;
  confirmedPurchases: number;
  sellableStockValue: number;
  stockMovements: number;
}

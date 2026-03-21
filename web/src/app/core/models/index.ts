// ── Auth ──────────────────────────────────────────────────────────────────────

export interface AuthUser {
  id: string;
  phone: string;
  firstName: string;
  lastName: string;
  email?: string;
  role: 'PHARMACY_OWNER' | 'PHARMACY_STAFF' | 'ADMIN';
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

// ── Reservation ───────────────────────────────────────────────────────────────

export type ReservationStatus =
  | 'PENDING' | 'CONFIRMED' | 'PAID' | 'READY'
  | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';

export interface ReservationItem {
  medicationId:   string;
  medicationName: string;
  strength?:      string;
  quantity:       number;
  unitPrice:      number;
}

export interface Reservation {
  id:             string;
  reference:      string;
  status:         ReservationStatus;
  customerId:     string;
  customerName:   string;
  customerPhone:  string;
  pharmacyId:     string;
  pharmacyName:   string;
  totalAmount:    number;
  expiresAt?:     string;
  createdAt:      string;
  updatedAt:      string;
  items:          ReservationItem[];
}

export interface ReservationPage {
  content:       Reservation[];
  totalElements: number;
  totalPages:    number;
  number:        number;
}

// ── Stock ─────────────────────────────────────────────────────────────────────

export type StockLevel = 'OK' | 'LOW' | 'OUT';

export interface PharmacyStock {
  id:           string;
  medicationId: string;
  medicationName: string;
  genericName?:   string;
  category?:      string;
  form?:          string;
  strength?:      string;
  quantity:       number;
  reorderLevel:   number;
  unitPrice:      number;
  updatedAt:      string;
  level:          StockLevel;
}

export interface StockUpdateRequest {
  quantity:     number;
  reorderLevel: number;
  unitPrice:    number;
}

// ── Analytics ─────────────────────────────────────────────────────────────────

export interface DashboardKpis {
  reservationsToday:   number;
  revenueToday:        number;
  criticalStockCount:  number;
  averageRating:       number;
  pendingCount:        number;
  revenueChange:       number; // % vs yesterday
  reservationsChange:  number;
}

export interface TopMedication {
  medicationId:      string;
  medicationName:    string;
  reservationCount:  number;
  revenue:           number;
}

export interface HourlyStats {
  hour:             number;
  reservationCount: number;
}

export interface DailyStats {
  date:             string;
  reservationCount: number;
  revenue:          number;
}

export interface AnalyticsData {
  topMedications: TopMedication[];
  hourlyStats:    HourlyStats[];
  dailyStats:     DailyStats[];
  weeklyRevenue:  DailyStats[];
}

// ── Alert ─────────────────────────────────────────────────────────────────────

export type AlertType = 'STOCK_OUT' | 'STOCK_LOW' | 'PAYMENT_RECEIVED' | 'RESERVATION_EXPIRING';

export interface Alert {
  id:        string;
  type:      AlertType;
  title:     string;
  message:   string;
  read:      boolean;
  createdAt: string;
  metadata?: Record<string, unknown>;
}

// ── Pharmacy profile ──────────────────────────────────────────────────────────

export interface OpeningHours {
  monday?:    string;
  tuesday?:   string;
  wednesday?: string;
  thursday?:  string;
  friday?:    string;
  saturday?:  string;
  sunday?:    string;
}

export interface PharmacyProfile {
  id:           string;
  name:         string;
  address:      string;
  city:         string;
  phone:        string;
  email?:       string;
  latitude?:    number;
  longitude?:   number;
  openingHours: OpeningHours;
  rating:       number;
  reviewCount:  number;
  status:       'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
}

// ── WebSocket event ───────────────────────────────────────────────────────────

export interface WsEvent<T = unknown> {
  type:      string;
  payload:   T;
  timestamp: string;
}

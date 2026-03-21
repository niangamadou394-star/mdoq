import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  DashboardKpis, PharmacyStock, StockUpdateRequest,
  Reservation, ReservationPage, AnalyticsData,
  Alert, PharmacyProfile
} from '../models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  // ── Dashboard ─────────────────────────────────────────────────────────────

  getKpis(pharmacyId: string): Observable<DashboardKpis> {
    return this.http.get<DashboardKpis>(`/api/pharmacies/${pharmacyId}/kpis`);
  }

  // ── Stock ─────────────────────────────────────────────────────────────────

  getStock(pharmacyId: string): Observable<PharmacyStock[]> {
    return this.http.get<PharmacyStock[]>(`/api/pharmacies/${pharmacyId}/stock`);
  }

  updateStock(pharmacyId: string, stockId: string, body: StockUpdateRequest): Observable<PharmacyStock> {
    return this.http.put<PharmacyStock>(`/api/pharmacies/${pharmacyId}/stock/${stockId}`, body);
  }

  // ── Reservations ──────────────────────────────────────────────────────────

  getReservations(pharmacyId: string, params?: {
    status?: string;
    page?: number;
    size?: number;
  }): Observable<ReservationPage> {
    let p = new HttpParams();
    if (params?.status) p = p.set('status', params.status);
    if (params?.page   !== undefined) p = p.set('page',   params.page);
    if (params?.size   !== undefined) p = p.set('size',   params.size);
    return this.http.get<ReservationPage>(`/api/reservations/pharmacy/${pharmacyId}`, { params: p });
  }

  confirmReservation(id: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`/api/reservations/${id}/confirm`, {});
  }

  cancelReservation(id: string, reason?: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`/api/reservations/${id}/cancel`, { reason });
  }

  markReady(id: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`/api/reservations/${id}/complete`, {});
  }

  // ── Alerts ────────────────────────────────────────────────────────────────

  getAlerts(pharmacyId: string): Observable<Alert[]> {
    return this.http.get<Alert[]>(`/api/pharmacies/${pharmacyId}/alerts`);
  }

  markAlertRead(alertId: string): Observable<void> {
    return this.http.patch<void>(`/api/alerts/${alertId}/read`, {});
  }

  // ── Analytics ─────────────────────────────────────────────────────────────

  getAnalytics(pharmacyId: string, period: 'week' | 'month' = 'week'): Observable<AnalyticsData> {
    return this.http.get<AnalyticsData>(`/api/pharmacies/${pharmacyId}/analytics`, {
      params: { period }
    });
  }

  // ── Profile ───────────────────────────────────────────────────────────────

  getProfile(pharmacyId: string): Observable<PharmacyProfile> {
    return this.http.get<PharmacyProfile>(`/api/pharmacies/${pharmacyId}`);
  }

  updateProfile(pharmacyId: string, body: Partial<PharmacyProfile>): Observable<PharmacyProfile> {
    return this.http.put<PharmacyProfile>(`/api/pharmacies/${pharmacyId}`, body);
  }
}

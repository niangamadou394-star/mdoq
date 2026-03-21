import { Component, inject, OnInit, signal, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AnalyticsData } from '../../core/models';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="analytics-page animate-fade-in">
      <div class="page-header">
        <div>
          <h1 class="text-h2">Analytiques</h1>
          <p class="text-sm">Performance de votre pharmacie</p>
        </div>
        <div class="flex gap-md items-center">
          <select class="form-control" style="width:140px" [(ngModel)]="period" (ngModelChange)="load()">
            <option value="week">7 derniers jours</option>
            <option value="month">30 derniers jours</option>
          </select>
          <button class="btn btn-primary" (click)="load()">
            <span class="material-icons" style="font-size:16px">refresh</span>
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="analytics-grid">
          @for (_ of [0,1,2]; track $index) {
            <div class="skeleton" style="height:320px;border-radius:14px"></div>
          }
        </div>
      } @else if (data()) {
        <div class="analytics-grid">

          <!-- Daily revenue chart -->
          <div class="card chart-card">
            <div class="card-header">
              <h3 class="card-title">Chiffre d'affaires quotidien</h3>
              <span class="text-sm">FCFA</span>
            </div>
            <div class="chart-container">
              <div class="bar-chart">
                @for (day of data()!.dailyStats; track day.date) {
                  <div class="bar-group">
                    <div class="bar-wrap">
                      <div
                        class="bar bar-revenue"
                        [style.height.%]="barHeightRevenue(day.revenue)"
                        [title]="day.revenue | number:'1.0-0' + ' FCFA'"
                      ></div>
                    </div>
                    <span class="bar-label">{{ day.date | date:'dd/MM':'':'fr' }}</span>
                  </div>
                }
              </div>
            </div>
            <!-- Y-axis legend -->
            <div class="chart-legend">
              <span class="legend-dot" style="background:var(--color-primary)"></span>
              Total FCFA sur la période : {{ totalRevenue() | number:'1.0-0' }} FCFA
            </div>
          </div>

          <!-- Daily reservations -->
          <div class="card chart-card">
            <div class="card-header">
              <h3 class="card-title">Réservations par jour</h3>
              <span class="text-sm">Nombre</span>
            </div>
            <div class="chart-container">
              <div class="bar-chart">
                @for (day of data()!.dailyStats; track day.date) {
                  <div class="bar-group">
                    <div class="bar-wrap">
                      <div
                        class="bar bar-count"
                        [style.height.%]="barHeightCount(day.reservationCount)"
                        [title]="day.reservationCount + ' réservation(s)'"
                      ></div>
                    </div>
                    <span class="bar-label">{{ day.date | date:'dd/MM':'':'fr' }}</span>
                  </div>
                }
              </div>
            </div>
            <div class="chart-legend">
              <span class="legend-dot" style="background:var(--color-accent)"></span>
              Total : {{ totalReservations() }} réservations
            </div>
          </div>

          <!-- Peak hours chart -->
          <div class="card chart-card">
            <div class="card-header">
              <h3 class="card-title">Heures de pointe</h3>
              <span class="text-sm">Réservations / heure</span>
            </div>
            <div class="chart-container">
              <div class="bar-chart bar-chart-hourly">
                @for (h of data()!.hourlyStats; track h.hour) {
                  <div class="bar-group bar-group-sm">
                    <div class="bar-wrap">
                      <div
                        class="bar bar-hourly"
                        [style.height.%]="barHeightHour(h.reservationCount)"
                        [title]="h.hour + 'h00 — ' + h.reservationCount + ' réservations'"
                        [class.peak]="isPeak(h.reservationCount)"
                      ></div>
                    </div>
                    @if (h.hour % 4 === 0) {
                      <span class="bar-label">{{ h.hour }}h</span>
                    }
                  </div>
                }
              </div>
            </div>
            <div class="chart-legend">
              <span class="legend-dot" style="background:var(--color-success)"></span>
              Heure de pointe : {{ peakHour() }}h00 – {{ peakHour() + 1 }}h00
            </div>
          </div>

          <!-- Top medications table -->
          <div class="card top-meds-card">
            <div class="card-header">
              <h3 class="card-title">Top médicaments demandés</h3>
              <span class="badge badge-info">{{ period === 'week' ? '7j' : '30j' }}</span>
            </div>
            <table class="data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Médicament</th>
                  <th style="text-align:right">Réservations</th>
                  <th style="text-align:right">Revenu</th>
                  <th>Part</th>
                </tr>
              </thead>
              <tbody>
                @for (med of data()!.topMedications; track med.medicationId; let i = $index) {
                  <tr>
                    <td>
                      <span class="rank-badge" [class.gold]="i===0" [class.silver]="i===1" [class.bronze]="i===2">
                        {{ i + 1 }}
                      </span>
                    </td>
                    <td class="font-semibold" style="font-size:13px">{{ med.medicationName }}</td>
                    <td style="text-align:right">{{ med.reservationCount }}</td>
                    <td style="text-align:right;color:var(--color-primary);font-weight:600">
                      {{ med.revenue | number:'1.0-0' }} FCFA
                    </td>
                    <td style="width:120px">
                      <div class="progress-bar-wrap" style="height:6px">
                        <div class="progress-bar-fill level-ok"
                             [style.width.%]="sharePercent(med.reservationCount)"></div>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

        </div>
      }
    </div>
  `,
  styles: [`
    .analytics-page { max-width: 1200px; }

    .page-header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: var(--space-xl); flex-wrap: wrap; gap: var(--space-md);
    }

    .analytics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
      gap: var(--space-lg);
    }

    .top-meds-card { grid-column: 1 / -1; }

    .chart-card { min-height: 280px; }

    .chart-container {
      flex: 1;
      height: 200px;
      display: flex;
      align-items: flex-end;
      overflow: hidden;
    }

    .bar-chart {
      display: flex;
      align-items: flex-end;
      gap: 6px;
      width: 100%;
      height: 100%;
      padding-bottom: 24px;
    }

    .bar-chart-hourly { gap: 3px; }

    .bar-group {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      height: 100%;
      min-width: 0;
    }

    .bar-group-sm { flex: none; width: 14px; }

    .bar-wrap {
      flex: 1;
      display: flex;
      align-items: flex-end;
      width: 100%;
    }

    .bar {
      width: 100%;
      border-radius: 4px 4px 0 0;
      min-height: 2px;
      cursor: pointer;
      transition: opacity 0.15s;
      &:hover { opacity: 0.8; }
    }

    .bar-revenue { background: var(--color-primary); }
    .bar-count   { background: var(--color-accent); }
    .bar-hourly  { background: var(--color-border); }
    .bar-hourly.peak { background: var(--color-success); }

    .bar-label {
      font-size: 10px; color: var(--color-text-hint);
      margin-top: 4px; white-space: nowrap;
    }

    .chart-legend {
      display: flex; align-items: center; gap: 6px;
      font-size: 12px; color: var(--color-text-secondary);
      margin-top: var(--space-sm);
    }

    .legend-dot {
      width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0;
    }

    .rank-badge {
      width: 24px; height: 24px; border-radius: 50%;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 11px; font-weight: 700;
      background: var(--color-border-light);
      color: var(--color-text-secondary);

      &.gold   { background: #fef3c7; color: #92400e; }
      &.silver { background: #f1f5f9; color: #334155; }
      &.bronze { background: #fef9f0; color: #7c3d12; }
    }
  `],
})
export class AnalyticsComponent implements OnInit {
  period  = 'week';
  loading = signal(true);
  data    = signal<AnalyticsData | null>(null);

  private api  = inject(ApiService);
  private auth = inject(AuthService);

  ngOnInit(): void { this.load(); }

  load(): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    if (!pharmacyId) { this.loading.set(false); return; }
    this.loading.set(true);
    this.api.getAnalytics(pharmacyId, this.period as 'week' | 'month').subscribe({
      next:  d => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  // ── Chart helpers ─────────────────────────────────────────────────────────

  private maxRevenue(): number {
    const d = this.data();
    if (!d?.dailyStats.length) return 1;
    return Math.max(...d.dailyStats.map(s => s.revenue), 1);
  }

  private maxCount(): number {
    const d = this.data();
    if (!d?.dailyStats.length) return 1;
    return Math.max(...d.dailyStats.map(s => s.reservationCount), 1);
  }

  private maxHour(): number {
    const d = this.data();
    if (!d?.hourlyStats.length) return 1;
    return Math.max(...d.hourlyStats.map(h => h.reservationCount), 1);
  }

  barHeightRevenue(v: number): number { return Math.round((v / this.maxRevenue()) * 100); }
  barHeightCount(v: number):   number { return Math.round((v / this.maxCount())   * 100); }
  barHeightHour(v: number):    number { return Math.round((v / this.maxHour())    * 100); }
  isPeak(v: number): boolean          { return v === this.maxHour(); }

  totalRevenue(): number {
    return this.data()?.dailyStats.reduce((s, d) => s + d.revenue, 0) ?? 0;
  }

  totalReservations(): number {
    return this.data()?.dailyStats.reduce((s, d) => s + d.reservationCount, 0) ?? 0;
  }

  peakHour(): number {
    const d = this.data();
    if (!d?.hourlyStats.length) return 0;
    return d.hourlyStats.reduce((best, h) =>
      h.reservationCount > best.reservationCount ? h : best
    ).hour;
  }

  sharePercent(count: number): number {
    const total = this.totalReservations();
    return total ? Math.round((count / total) * 100) : 0;
  }
}

import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { DashboardKpis } from '../../core/models';

interface KpiCard {
  label:    string;
  value:    string | number;
  icon:     string;
  color:    string;
  change?:  number;
  suffix?:  string;
  link?:    string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard animate-fade-in">
      <div class="page-header">
        <div>
          <h1 class="text-h2">Tableau de bord</h1>
          <p class="text-sm mt-4">{{ today | date:'EEEE d MMMM yyyy':'':'fr' }}</p>
        </div>
        <button class="btn btn-primary" (click)="refresh()">
          <span class="material-icons" style="font-size:18px">refresh</span>
          Actualiser
        </button>
      </div>

      <!-- KPI Cards -->
      @if (loading()) {
        <div class="kpi-grid">
          @for (_ of [0,1,2,3]; track $index) {
            <div class="kpi-skeleton skeleton" style="height:120px;border-radius:14px"></div>
          }
        </div>
      } @else if (kpis()) {
        <div class="kpi-grid">
          @for (card of kpiCards(); track card.label) {
            <div class="kpi-card" [style.--card-color]="card.color"
                 [routerLink]="card.link ?? null">
              <div class="kpi-icon">
                <span class="material-icons">{{ card.icon }}</span>
              </div>
              <div class="kpi-body">
                <p class="kpi-label">{{ card.label }}</p>
                <p class="kpi-value">
                  {{ card.value }}
                  @if (card.suffix) { <span class="kpi-suffix">{{ card.suffix }}</span> }
                </p>
                @if (card.change !== undefined) {
                  <p class="kpi-change" [class.positive]="card.change >= 0">
                    <span class="material-icons" style="font-size:13px">
                      {{ card.change >= 0 ? 'arrow_upward' : 'arrow_downward' }}
                    </span>
                    {{ card.change | number:'1.0-1' }}% vs hier
                  </p>
                }
              </div>
            </div>
          }
        </div>
      }

      <!-- Quick actions row -->
      <div class="quick-actions">
        <h3 class="text-h4 mb-md">Actions rapides</h3>
        <div class="action-grid">
          <a routerLink="../reservations" class="action-card">
            <span class="material-icons action-icon" style="color:var(--color-confirmed)">event_note</span>
            <span>Gérer les réservations</span>
            @if (kpis()?.pendingCount) {
              <span class="badge badge-warning" style="margin-left:auto">
                {{ kpis()!.pendingCount }} en attente
              </span>
            }
          </a>
          <a routerLink="../stock" class="action-card">
            <span class="material-icons action-icon" style="color:var(--color-warning)">inventory_2</span>
            <span>Vérifier le stock</span>
            @if (kpis()?.criticalStockCount) {
              <span class="badge badge-error" style="margin-left:auto">
                {{ kpis()!.criticalStockCount }} alertes
              </span>
            }
          </a>
          <a routerLink="../analytics" class="action-card">
            <span class="material-icons action-icon" style="color:var(--color-accent)">bar_chart</span>
            <span>Voir les analytiques</span>
          </a>
          <a routerLink="../alerts" class="action-card">
            <span class="material-icons action-icon" style="color:var(--color-error)">notifications_active</span>
            <span>Consulter les alertes</span>
          </a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard { max-width: 1200px; }

    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      margin-bottom: var(--space-xl);
    }

    /* KPI grid */
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: var(--space-md);
      margin-bottom: var(--space-xl);
    }

    .kpi-skeleton { min-width: 220px; }

    .kpi-card {
      background: var(--color-surface);
      border-radius: var(--radius-lg);
      border: 1px solid var(--color-border);
      padding: var(--space-lg);
      display: flex;
      gap: var(--space-md);
      align-items: flex-start;
      box-shadow: var(--shadow-sm);
      cursor: pointer;
      transition: box-shadow 0.15s, transform 0.15s;
      text-decoration: none;

      &:hover {
        box-shadow: var(--shadow-md);
        transform: translateY(-2px);
      }
    }

    .kpi-icon {
      width: 48px;
      height: 48px;
      border-radius: var(--radius-md);
      background: color-mix(in srgb, var(--card-color) 12%, transparent);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      .material-icons {
        font-size: 24px;
        color: var(--card-color);
      }
    }

    .kpi-body { flex: 1; min-width: 0; }
    .kpi-label { font-size: 12px; color: var(--color-text-secondary); font-weight: 500; }
    .kpi-value {
      font-size: 26px;
      font-weight: 700;
      color: var(--color-text-primary);
      line-height: 1.2;
      margin: 2px 0;
    }
    .kpi-suffix { font-size: 14px; font-weight: 400; color: var(--color-text-secondary); }
    .kpi-change {
      display: flex;
      align-items: center;
      gap: 2px;
      font-size: 11px;
      color: var(--color-error);

      &.positive { color: var(--color-success); }
    }

    /* Quick actions */
    .action-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: var(--space-md);
    }

    .action-card {
      background: var(--color-surface);
      border-radius: var(--radius-md);
      border: 1px solid var(--color-border);
      padding: var(--space-md);
      display: flex;
      align-items: center;
      gap: var(--space-md);
      font-size: 14px;
      font-weight: 500;
      color: var(--color-text-primary);
      text-decoration: none;
      transition: all 0.15s;
      cursor: pointer;

      &:hover { background: var(--color-border-light); transform: translateX(2px); }
    }

    .action-icon { font-size: 24px; }
  `],
})
export class DashboardComponent implements OnInit {
  today   = new Date();
  loading = signal(true);
  kpis    = signal<DashboardKpis | null>(null);

  private api  = inject(ApiService);
  private auth = inject(AuthService);

  kpiCards = () => {
    const k = this.kpis();
    if (!k) return [];
    return [
      {
        label: 'Réservations aujourd\'hui',
        value: k.reservationsToday,
        icon:  'event_note',
        color: 'var(--color-confirmed)',
        change: k.reservationsChange,
        link:  '../reservations',
      },
      {
        label:  'Chiffre d\'affaires',
        value:  k.revenueToday.toLocaleString('fr-SN'),
        suffix: 'FCFA',
        icon:   'payments',
        color:  'var(--color-success)',
        change: k.revenueChange,
      },
      {
        label: 'Alertes stock critique',
        value: k.criticalStockCount,
        icon:  'warning',
        color: k.criticalStockCount > 0 ? 'var(--color-error)' : 'var(--color-success)',
        link:  '../stock',
      },
      {
        label:  'Note clients',
        value:  k.averageRating.toFixed(1),
        suffix: '/ 5 ⭐',
        icon:   'star',
        color:  'var(--color-warning)',
      },
    ] as KpiCard[];
  };

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    if (!pharmacyId) { this.loading.set(false); return; }

    this.loading.set(true);
    this.api.getKpis(pharmacyId).subscribe({
      next:  k => { this.kpis.set(k); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}

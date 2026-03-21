import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { Alert, AlertType } from '../../core/models';

@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="alerts-page animate-fade-in">
      <div class="page-header">
        <div>
          <h1 class="text-h2">Alertes</h1>
          <p class="text-sm">{{ unreadCount() }} non lue(s)</p>
        </div>
        <div class="flex gap-md">
          @if (unreadCount() > 0) {
            <button class="btn btn-outline" (click)="markAllRead()">
              <span class="material-icons" style="font-size:16px">done_all</span>
              Tout marquer comme lu
            </button>
          }
          <button class="btn btn-primary" (click)="load()">
            <span class="material-icons" style="font-size:16px">refresh</span>
          </button>
        </div>
      </div>

      <!-- Filter tabs -->
      <div class="alert-tabs">
        @for (tab of tabs; track tab.type) {
          <button
            class="alert-tab"
            [class.active]="activeTab() === tab.type"
            (click)="activeTab.set(tab.type)"
          >
            <span class="material-icons" style="font-size:16px">{{ tab.icon }}</span>
            {{ tab.label }}
            @if (countByType(tab.type) > 0) {
              <span class="tab-badge">{{ countByType(tab.type) }}</span>
            }
          </button>
        }
      </div>

      @if (loading()) {
        <div class="flex" style="gap:16px;flex-direction:column">
          @for (_ of [0,1,2,3]; track $index) {
            <div class="skeleton" style="height:80px;border-radius:10px"></div>
          }
        </div>
      } @else {
        <div class="alerts-list">
          @for (alert of filtered(); track alert.id) {
            <div class="alert-card" [class.unread]="!alert.read" [class]="'alert-' + alertColor(alert.type)">
              <div class="alert-icon">
                <span class="material-icons">{{ alertIcon(alert.type) }}</span>
              </div>
              <div class="alert-body">
                <div class="alert-title">{{ alert.title }}</div>
                <div class="alert-message">{{ alert.message }}</div>
                <div class="alert-time text-xs">{{ alert.createdAt | date:'dd/MM HH:mm':'':'fr' }}</div>
              </div>
              <div class="alert-actions">
                @if (!alert.read) {
                  <button class="btn btn-ghost btn-icon" (click)="markRead(alert)" title="Marquer comme lu">
                    <span class="material-icons" style="font-size:16px">check</span>
                  </button>
                }
                @if (alert.type === 'STOCK_OUT' || alert.type === 'STOCK_LOW') {
                  <a class="btn btn-outline btn-sm" routerLink="../stock">
                    Gérer le stock
                  </a>
                }
                @if (alert.type === 'RESERVATION_EXPIRING') {
                  <a class="btn btn-outline btn-sm" routerLink="../reservations">
                    Voir
                  </a>
                }
              </div>
            </div>
          }

          @if (filtered().length === 0) {
            <div class="empty-alerts">
              <span class="material-icons">notifications_none</span>
              <p>Aucune alerte{{ activeTab() !== 'ALL' ? ' dans cette catégorie' : '' }}</p>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .alerts-page { max-width: 900px; }

    .page-header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: var(--space-lg); flex-wrap: wrap; gap: var(--space-md);
    }

    .alert-tabs {
      display: flex; gap: var(--space-sm); margin-bottom: var(--space-lg);
      overflow-x: auto; padding-bottom: 2px;
    }

    .alert-tab {
      display: flex; align-items: center; gap: 6px;
      padding: 7px 14px; border-radius: var(--radius-full);
      border: 1px solid var(--color-border); background: var(--color-surface);
      font-size: 13px; cursor: pointer; white-space: nowrap; transition: all 0.15s;
      &:hover { background: var(--color-border-light); }
      &.active { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
    }

    .tab-badge {
      background: var(--color-error); color: #fff;
      border-radius: var(--radius-full); font-size: 10px; font-weight: 700;
      min-width: 16px; height: 16px; padding: 0 4px;
      display: flex; align-items: center; justify-content: center;
    }

    .alerts-list { display: flex; flex-direction: column; gap: var(--space-md); }

    .alert-card {
      display: flex; align-items: flex-start; gap: var(--space-md);
      background: var(--color-surface); border-radius: var(--radius-md);
      border: 1px solid var(--color-border); padding: var(--space-md);
      transition: box-shadow 0.15s;

      &.unread { border-left: 4px solid var(--color-primary); }
      &.alert-error   { border-left-color: var(--color-error);   }
      &.alert-warning { border-left-color: var(--color-warning); }
      &.alert-success { border-left-color: var(--color-success); }
      &.alert-info    { border-left-color: var(--color-accent);  }

      &:hover { box-shadow: var(--shadow-md); }
    }

    .alert-icon {
      width: 40px; height: 40px; border-radius: var(--radius-md); flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      background: var(--color-bg);
      .material-icons { font-size: 22px; color: var(--color-text-secondary); }
    }

    .alert-body   { flex: 1; min-width: 0; }
    .alert-title  { font-weight: 600; font-size: 14px; }
    .alert-message { font-size: 13px; color: var(--color-text-secondary); margin-top: 2px; }
    .alert-time   { margin-top: 6px; }

    .alert-actions { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; }

    .empty-alerts {
      text-align: center; padding: 48px; color: var(--color-text-secondary);
      .material-icons { font-size: 56px; opacity: .3; display: block; margin-bottom: 8px; }
    }
  `],
})
export class AlertsComponent implements OnInit {
  alerts      = signal<Alert[]>([]);
  loading     = signal(true);
  activeTab   = signal<string>('ALL');

  private api   = inject(ApiService);
  private auth  = inject(AuthService);
  private toast = inject(ToastService);

  readonly tabs = [
    { type: 'ALL',                    label: 'Toutes',          icon: 'notifications' },
    { type: 'STOCK_OUT',              label: 'Ruptures',        icon: 'error' },
    { type: 'STOCK_LOW',              label: 'Stock bas',       icon: 'warning' },
    { type: 'PAYMENT_RECEIVED',       label: 'Paiements',       icon: 'payments' },
    { type: 'RESERVATION_EXPIRING',   label: 'Expirations',     icon: 'timer' },
  ];

  readonly unreadCount = computed(() => this.alerts().filter(a => !a.read).length);

  readonly filtered = computed(() => {
    const tab = this.activeTab();
    if (tab === 'ALL') return this.alerts();
    return this.alerts().filter(a => a.type === tab);
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    if (!pharmacyId) { this.loading.set(false); return; }
    this.loading.set(true);
    this.api.getAlerts(pharmacyId).subscribe({
      next:  list => { this.alerts.set(list); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  markRead(alert: Alert): void {
    this.api.markAlertRead(alert.id).subscribe({
      next: () => {
        this.alerts.update(list =>
          list.map(a => a.id === alert.id ? { ...a, read: true } : a)
        );
      },
      error: () => this.toast.error('Erreur.'),
    });
  }

  markAllRead(): void {
    const unread = this.alerts().filter(a => !a.read);
    unread.forEach(a => this.markRead(a));
  }

  countByType(type: string): number {
    if (type === 'ALL') return this.alerts().filter(a => !a.read).length;
    return this.alerts().filter(a => a.type === type && !a.read).length;
  }

  alertIcon(type: AlertType): string {
    const map: Record<AlertType, string> = {
      STOCK_OUT:            'error',
      STOCK_LOW:            'warning',
      PAYMENT_RECEIVED:     'payments',
      RESERVATION_EXPIRING: 'timer',
    };
    return map[type] ?? 'notifications';
  }

  alertColor(type: AlertType): string {
    const map: Record<AlertType, string> = {
      STOCK_OUT:            'error',
      STOCK_LOW:            'warning',
      PAYMENT_RECEIVED:     'success',
      RESERVATION_EXPIRING: 'info',
    };
    return map[type] ?? 'info';
  }
}

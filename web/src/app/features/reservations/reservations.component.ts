import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { ToastService } from '../../core/services/toast.service';
import { Reservation, ReservationStatus } from '../../core/models';

type FilterStatus = '' | 'PENDING' | 'CONFIRMED' | 'PAID' | 'READY' | 'COMPLETED' | 'CANCELLED';

@Component({
  selector: 'app-reservations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="res-page animate-fade-in">
      <div class="page-header">
        <div>
          <h1 class="text-h2">Réservations</h1>
          <div class="flex gap-sm items-center" style="margin-top:4px">
            <span class="text-sm">{{ total() }} réservation(s)</span>
            @if (wsConnected()) {
              <span class="ws-dot live"></span>
              <span class="text-xs" style="color:var(--color-success)">Temps réel</span>
            }
          </div>
        </div>
        <div class="flex gap-md items-center" style="flex-wrap:wrap">
          <input class="form-control" style="width:220px" type="search"
                 placeholder="Réf, client, médicament..."
                 [(ngModel)]="searchQuery" />
          <select class="form-control" style="width:160px" [(ngModel)]="statusFilter">
            <option value="">Tous les statuts</option>
            <option value="PENDING">En attente</option>
            <option value="CONFIRMED">Confirmées</option>
            <option value="PAID">Payées</option>
            <option value="READY">Prêtes</option>
            <option value="COMPLETED">Terminées</option>
            <option value="CANCELLED">Annulées</option>
          </select>
          <button class="btn btn-primary" (click)="load()">
            <span class="material-icons" style="font-size:16px">refresh</span>
          </button>
        </div>
      </div>

      <!-- Status tabs -->
      <div class="status-tabs">
        @for (tab of statusTabs; track tab.status) {
          <button
            class="status-tab"
            [class.active]="statusFilter === tab.status"
            (click)="statusFilter = tab.status; load()"
          >
            {{ tab.label }}
            @if (tab.count > 0) {
              <span class="tab-count" [class]="tab.countClass">{{ tab.count }}</span>
            }
          </button>
        }
      </div>

      <!-- Table -->
      <div class="card" style="padding:0;overflow:hidden">
        @if (loading()) {
          <div style="padding:48px;text-align:center">
            <div class="spinner" style="margin:auto"></div>
          </div>
        } @else {
          <table class="data-table">
            <thead>
              <tr>
                <th>Référence</th>
                <th>Client</th>
                <th>Médicaments</th>
                <th style="text-align:right">Montant</th>
                <th>Statut</th>
                <th>Heure</th>
                <th style="text-align:center">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (r of filtered(); track r.id) {
                <tr class="res-row" [class.row-new]="isNew(r.id)">
                  <td>
                    <span class="ref-chip">{{ r.reference }}</span>
                  </td>
                  <td>
                    <div class="font-semibold" style="font-size:13px">{{ r.customerName }}</div>
                    <div class="text-xs">{{ r.customerPhone }}</div>
                  </td>
                  <td class="text-sm">
                    {{ r.items.map(i => i.medicationName + ' ×' + i.quantity).join(', ') }}
                  </td>
                  <td style="text-align:right;font-weight:600;color:var(--color-primary)">
                    {{ r.totalAmount | number:'1.0-0' }} FCFA
                  </td>
                  <td>
                    <span class="badge" [class]="statusBadge(r.status)">
                      {{ statusLabel(r.status) }}
                    </span>
                  </td>
                  <td class="text-xs">
                    {{ r.createdAt | date:'HH:mm':'':'fr' }}
                    <br>{{ r.createdAt | date:'dd/MM':'':'fr' }}
                  </td>
                  <td>
                    <div class="action-btns">
                      @if (r.status === 'PENDING') {
                        <button class="btn btn-success btn-sm"
                                (click)="confirm(r)"
                                [disabled]="actionLoading() === r.id"
                                title="Confirmer">
                          <span class="material-icons" style="font-size:15px">check</span>
                          Confirmer
                        </button>
                      }
                      @if (r.status === 'CONFIRMED' || r.status === 'PAID') {
                        <button class="btn btn-accent btn-sm"
                                (click)="markReady(r)"
                                [disabled]="actionLoading() === r.id"
                                title="Marquer prête">
                          <span class="material-icons" style="font-size:15px">storefront</span>
                          Prête
                        </button>
                      }
                      @if (r.status === 'PENDING' || r.status === 'CONFIRMED') {
                        <button class="btn btn-ghost btn-sm"
                                (click)="cancel(r)"
                                [disabled]="actionLoading() === r.id"
                                title="Annuler">
                          <span class="material-icons" style="font-size:15px">close</span>
                        </button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>

          @if (filtered().length === 0) {
            <div style="padding:48px;text-align:center;color:var(--color-text-secondary)">
              <span class="material-icons" style="font-size:56px;display:block;margin-bottom:8px;opacity:.3">
                event_note
              </span>
              Aucune réservation {{ statusFilter ? 'avec ce statut' : '' }}
            </div>
          }
        }
      </div>

      <!-- Pagination -->
      @if (totalPages() > 1) {
        <div class="pagination">
          <button class="btn btn-ghost btn-sm"
                  [disabled]="page() === 0"
                  (click)="goPage(page() - 1)">‹ Précédent</button>
          <span class="text-sm">Page {{ page() + 1 }} / {{ totalPages() }}</span>
          <button class="btn btn-ghost btn-sm"
                  [disabled]="page() === totalPages() - 1"
                  (click)="goPage(page() + 1)">Suivant ›</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .res-page { max-width: 1300px; }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: var(--space-md);
      flex-wrap: wrap;
      gap: var(--space-md);
    }

    .ws-dot {
      width: 8px; height: 8px;
      border-radius: 50%;
      background: var(--color-text-secondary);
      &.live { background: var(--color-success); animation: pulse 1.5s infinite; }
    }

    /* Status tabs */
    .status-tabs {
      display: flex;
      gap: 4px;
      margin-bottom: var(--space-md);
      overflow-x: auto;
      padding-bottom: 2px;
    }

    .status-tab {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: var(--radius-full);
      border: 1px solid var(--color-border);
      background: var(--color-surface);
      font-size: 13px;
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.15s;

      &:hover { background: var(--color-border-light); }
      &.active { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
    }

    .tab-count {
      min-width: 18px; height: 18px; padding: 0 5px;
      border-radius: var(--radius-full);
      font-size: 10px; font-weight: 700;
      display: flex; align-items: center; justify-content: center;
      background: var(--color-error); color: #fff;
    }

    .ref-chip {
      font-family: monospace;
      font-size: 12px;
      background: var(--color-border-light);
      padding: 2px 8px;
      border-radius: var(--radius-sm);
    }

    .action-btns {
      display: flex;
      gap: 4px;
      justify-content: center;
    }

    .row-new td { background: #eff6ff !important; animation: fadeIn 0.5s; }

    .pagination {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--space-md);
      margin-top: var(--space-md);
    }
  `],
})
export class ReservationsComponent implements OnInit, OnDestroy {
  reservations  = signal<Reservation[]>([]);
  loading       = signal(true);
  actionLoading = signal<string | null>(null);
  wsConnected   = signal(false);
  newIds        = signal<Set<string>>(new Set());
  total         = signal(0);
  page          = signal(0);
  totalPages    = signal(1);
  searchQuery   = '';
  statusFilter: FilterStatus = '';
  private subs  = new Subscription();

  private api   = inject(ApiService);
  private auth  = inject(AuthService);
  private ws    = inject(WebSocketService);
  private toast = inject(ToastService);

  readonly filtered = computed(() => {
    const q = this.searchQuery.toLowerCase().trim();
    if (!q) return this.reservations();
    return this.reservations().filter(r =>
      r.reference.toLowerCase().includes(q) ||
      r.customerName.toLowerCase().includes(q) ||
      r.customerPhone.includes(q) ||
      r.items.some(i => i.medicationName.toLowerCase().includes(q))
    );
  });

  readonly statusTabs = [
    { status: '' as FilterStatus,        label: 'Toutes',    count: 0,  countClass: '' },
    { status: 'PENDING' as FilterStatus, label: 'En attente', count: 0, countClass: 'count-pending' },
    { status: 'CONFIRMED' as FilterStatus, label: 'Confirmées', count: 0, countClass: '' },
    { status: 'READY' as FilterStatus,   label: 'Prêtes',    count: 0,  countClass: '' },
  ];

  ngOnInit(): void {
    this.load();
    this.wsConnected.set(true);

    this.subs.add(
      this.ws.reservations$.subscribe(event => {
        const r = event.reservation;
        if (event.type === 'NEW') {
          this.reservations.update(list => [r, ...list]);
          this.newIds.update(s => { const ns = new Set(s); ns.add(r.id); return ns; });
          setTimeout(() => {
            this.newIds.update(s => { const ns = new Set(s); ns.delete(r.id); return ns; });
          }, 3000);
        } else {
          this.reservations.update(list => list.map(x => x.id === r.id ? r : x));
        }
      })
    );
  }

  ngOnDestroy(): void { this.subs.unsubscribe(); }

  load(): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    if (!pharmacyId) { this.loading.set(false); return; }
    this.loading.set(true);

    this.api.getReservations(pharmacyId, {
      status: this.statusFilter || undefined,
      page:   this.page(),
      size:   20,
    }).subscribe({
      next: page => {
        this.reservations.set(page.content);
        this.total.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  goPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  confirm(r: Reservation): void {
    this.actionLoading.set(r.id);
    this.api.confirmReservation(r.id).subscribe({
      next: updated => {
        this._replace(updated);
        this.actionLoading.set(null);
        this.toast.success(`Réservation ${r.reference} confirmée.`);
      },
      error: () => { this.actionLoading.set(null); this.toast.error('Erreur lors de la confirmation.'); },
    });
  }

  markReady(r: Reservation): void {
    this.actionLoading.set(r.id);
    this.api.markReady(r.id).subscribe({
      next: updated => {
        this._replace(updated);
        this.actionLoading.set(null);
        this.toast.success(`Commande ${r.reference} prête.`);
      },
      error: () => { this.actionLoading.set(null); this.toast.error('Erreur.'); },
    });
  }

  cancel(r: Reservation): void {
    if (!confirm(`Annuler ${r.reference} ?`)) return;
    this.actionLoading.set(r.id);
    this.api.cancelReservation(r.id).subscribe({
      next: updated => {
        this._replace(updated);
        this.actionLoading.set(null);
        this.toast.warning(`Réservation ${r.reference} annulée.`);
      },
      error: () => { this.actionLoading.set(null); this.toast.error('Erreur lors de l\'annulation.'); },
    });
  }

  isNew(id: string): boolean { return this.newIds().has(id); }

  statusLabel(s: ReservationStatus): string {
    const map: Record<ReservationStatus, string> = {
      PENDING: 'En attente', CONFIRMED: 'Confirmée', PAID: 'Payée',
      READY: 'Prête', COMPLETED: 'Terminée', CANCELLED: 'Annulée', EXPIRED: 'Expirée',
    };
    return map[s] ?? s;
  }

  statusBadge(s: ReservationStatus): string {
    const map: Record<ReservationStatus, string> = {
      PENDING: 'badge-pending', CONFIRMED: 'badge-confirmed', PAID: 'badge-paid',
      READY: 'badge-ready', COMPLETED: 'badge-completed',
      CANCELLED: 'badge-cancelled', EXPIRED: 'badge-expired',
    };
    return map[s] ?? 'badge-neutral';
  }

  private _replace(r: Reservation): void {
    this.reservations.update(list => list.map(x => x.id === r.id ? r : x));
  }
}

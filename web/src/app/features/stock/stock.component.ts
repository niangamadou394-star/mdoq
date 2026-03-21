import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PharmacyStock, StockLevel } from '../../core/models';

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="stock-page animate-fade-in">
      <div class="page-header">
        <div>
          <h1 class="text-h2">Gestion du stock</h1>
          <p class="text-sm">{{ filtered().length }} médicament(s)</p>
        </div>
        <div class="flex gap-md items-center">
          <input
            class="form-control"
            style="width:240px"
            type="search"
            placeholder="Rechercher..."
            [(ngModel)]="searchQuery"
          />
          <select class="form-control" style="width:160px" [(ngModel)]="levelFilter">
            <option value="">Tous les niveaux</option>
            <option value="OUT">Rupture</option>
            <option value="LOW">Stock bas</option>
            <option value="OK">Stock OK</option>
          </select>
          <button class="btn btn-primary" (click)="load()">
            <span class="material-icons" style="font-size:16px">refresh</span>
          </button>
        </div>
      </div>

      <!-- Summary chips -->
      <div class="stock-summary">
        <div class="summary-chip chip-out">
          <span class="material-icons" style="font-size:16px">error</span>
          {{ outCount() }} rupture(s)
        </div>
        <div class="summary-chip chip-low">
          <span class="material-icons" style="font-size:16px">warning</span>
          {{ lowCount() }} stock bas
        </div>
        <div class="summary-chip chip-ok">
          <span class="material-icons" style="font-size:16px">check_circle</span>
          {{ okCount() }} OK
        </div>
      </div>

      <!-- Table -->
      <div class="card" style="padding:0;overflow:hidden">
        @if (loading()) {
          <div style="padding:40px;text-align:center">
            <div class="spinner" style="margin:auto"></div>
          </div>
        } @else {
          <table class="data-table">
            <thead>
              <tr>
                <th>Médicament</th>
                <th>Catégorie</th>
                <th style="width:180px">Niveau de stock</th>
                <th style="text-align:right">Quantité</th>
                <th style="text-align:right">Seuil alerte</th>
                <th style="text-align:right">Prix (FCFA)</th>
                <th style="text-align:center">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (item of filtered(); track item.id) {
                <tr [class.row-out]="item.level === 'OUT'" [class.row-low]="item.level === 'LOW'">
                  <td>
                    <div class="med-name">{{ item.medicationName }}</div>
                    @if (item.strength) {
                      <div class="text-xs">{{ item.strength }}</div>
                    }
                  </td>
                  <td class="text-sm">{{ item.category ?? '—' }}</td>
                  <td>
                    <div class="stock-level-cell">
                      <div class="progress-bar-wrap">
                        <div
                          class="progress-bar-fill"
                          [class]="'level-' + item.level.toLowerCase()"
                          [style.width.%]="stockPercent(item)"
                        ></div>
                      </div>
                      <span class="badge" [class]="badgeClass(item.level)">
                        {{ levelLabel(item.level) }}
                      </span>
                    </div>
                  </td>
                  <td style="text-align:right">
                    @if (editing()?.id === item.id) {
                      <input
                        class="form-control form-control-sm"
                        type="number"
                        min="0"
                        style="width:80px;text-align:right"
                        [(ngModel)]="editQty"
                      />
                    } @else {
                      <strong>{{ item.quantity }}</strong>
                    }
                  </td>
                  <td style="text-align:right">
                    @if (editing()?.id === item.id) {
                      <input
                        class="form-control form-control-sm"
                        type="number"
                        min="0"
                        style="width:80px;text-align:right"
                        [(ngModel)]="editReorder"
                      />
                    } @else {
                      {{ item.reorderLevel }}
                    }
                  </td>
                  <td style="text-align:right">
                    @if (editing()?.id === item.id) {
                      <input
                        class="form-control form-control-sm"
                        type="number"
                        min="0"
                        step="50"
                        style="width:90px;text-align:right"
                        [(ngModel)]="editPrice"
                      />
                    } @else {
                      {{ item.unitPrice | number:'1.0-0' }}
                    }
                  </td>
                  <td style="text-align:center">
                    @if (editing()?.id === item.id) {
                      <div class="flex gap-sm" style="justify-content:center">
                        <button class="btn btn-success btn-sm" (click)="save(item)" [disabled]="saving()">
                          @if (saving()) { <span class="spinner" style="width:14px;height:14px"></span> }
                          @else { ✓ }
                        </button>
                        <button class="btn btn-ghost btn-sm" (click)="cancelEdit()">✕</button>
                      </div>
                    } @else {
                      <button class="btn btn-ghost btn-icon btn-sm" (click)="startEdit(item)" title="Modifier">
                        <span class="material-icons" style="font-size:16px">edit</span>
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>

          @if (filtered().length === 0) {
            <div style="padding:40px;text-align:center;color:var(--color-text-secondary)">
              <span class="material-icons" style="font-size:48px;display:block;margin-bottom:8px">inventory_2</span>
              Aucun médicament trouvé
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .stock-page { max-width: 1200px; }
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      margin-bottom: var(--space-lg);
      flex-wrap: wrap;
      gap: var(--space-md);
    }

    .stock-summary {
      display: flex;
      gap: var(--space-md);
      margin-bottom: var(--space-lg);
    }

    .summary-chip {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: var(--radius-full);
      font-size: 13px;
      font-weight: 600;

      &.chip-out { background: #fee2e2; color: var(--color-error); }
      &.chip-low { background: #fef3c7; color: #92400e; }
      &.chip-ok  { background: #dcfce7; color: #166534; }
    }

    .med-name { font-weight: 500; font-size: 13px; }

    .stock-level-cell {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .row-out { background: #fff5f5 !important; }
    .row-low { background: #fffbeb !important; }

    tr.row-out:hover td { background: #fee2e2 !important; }
    tr.row-low:hover td { background: #fef3c7 !important; }
  `],
})
export class StockComponent implements OnInit {
  stock       = signal<PharmacyStock[]>([]);
  loading     = signal(true);
  saving      = signal(false);
  editing     = signal<PharmacyStock | null>(null);
  searchQuery = '';
  levelFilter = '';
  editQty     = 0;
  editReorder = 0;
  editPrice   = 0;

  private api  = inject(ApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  readonly filtered = computed(() => {
    let list = this.stock();
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(s =>
        s.medicationName.toLowerCase().includes(q) ||
        (s.genericName ?? '').toLowerCase().includes(q)
      );
    }
    if (this.levelFilter) {
      list = list.filter(s => s.level === this.levelFilter);
    }
    return list;
  });

  readonly outCount = computed(() => this.stock().filter(s => s.level === 'OUT').length);
  readonly lowCount = computed(() => this.stock().filter(s => s.level === 'LOW').length);
  readonly okCount  = computed(() => this.stock().filter(s => s.level === 'OK').length);

  ngOnInit(): void { this.load(); }

  load(): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    if (!pharmacyId) { this.loading.set(false); return; }
    this.loading.set(true);
    this.api.getStock(pharmacyId).subscribe({
      next:  list => { this.stock.set(list); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  startEdit(item: PharmacyStock): void {
    this.editing.set(item);
    this.editQty     = item.quantity;
    this.editReorder = item.reorderLevel;
    this.editPrice   = item.unitPrice;
  }

  cancelEdit(): void { this.editing.set(null); }

  save(item: PharmacyStock): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    if (!pharmacyId) return;
    this.saving.set(true);

    this.api.updateStock(pharmacyId, item.id, {
      quantity:     this.editQty,
      reorderLevel: this.editReorder,
      unitPrice:    this.editPrice,
    }).subscribe({
      next: updated => {
        this.stock.update(list =>
          list.map(s => s.id === updated.id ? updated : s)
        );
        this.editing.set(null);
        this.saving.set(false);
        this.toast.success(`Stock mis à jour : ${updated.medicationName}`);
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Erreur lors de la mise à jour.');
      },
    });
  }

  stockPercent(item: PharmacyStock): number {
    if (item.reorderLevel === 0) return item.quantity > 0 ? 100 : 0;
    const max = item.reorderLevel * 3;
    return Math.min(100, Math.round((item.quantity / max) * 100));
  }

  levelLabel(l: StockLevel): string {
    return l === 'OK' ? 'Disponible' : l === 'LOW' ? 'Stock bas' : 'Rupture';
  }

  badgeClass(l: StockLevel): string {
    return l === 'OK' ? 'badge-success' : l === 'LOW' ? 'badge-warning' : 'badge-error';
  }
}

import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PharmacyProfile, OpeningHours } from '../../core/models';

interface DaySlot {
  key:    keyof OpeningHours;
  label:  string;
  value:  string;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="profile-page animate-fade-in">
      <div class="page-header">
        <div>
          <h1 class="text-h2">Ma pharmacie</h1>
          <p class="text-sm">Informations et configuration</p>
        </div>
        <div class="flex gap-md">
          @if (dirty()) {
            <button class="btn btn-outline" (click)="reset()">Annuler</button>
          }
          <button class="btn btn-primary"
                  [disabled]="saving() || !dirty()"
                  (click)="save()">
            @if (saving()) { <span class="spinner"></span> }
            @else { <span class="material-icons" style="font-size:16px">save</span> }
            Enregistrer
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="profile-grid">
          @for (_ of [0,1,2]; track $index) {
            <div class="skeleton" style="height:200px;border-radius:14px"></div>
          }
        </div>
      } @else if (form()) {
        <div class="profile-grid">

          <!-- General info -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">Informations générales</h3>
              <span class="badge" [class]="form()!.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'">
                {{ form()!.status }}
              </span>
            </div>

            <div class="form-grid">
              <div class="form-group">
                <label>Nom de la pharmacie *</label>
                <input class="form-control" type="text" [(ngModel)]="form()!.name"
                       (ngModelChange)="markDirty()" placeholder="Pharmacie du Plateau" />
              </div>
              <div class="form-group">
                <label>Téléphone *</label>
                <input class="form-control" type="tel" [(ngModel)]="form()!.phone"
                       (ngModelChange)="markDirty()" placeholder="+221 33 XXX XX XX" />
              </div>
              <div class="form-group">
                <label>Email</label>
                <input class="form-control" type="email" [(ngModel)]="form()!.email"
                       (ngModelChange)="markDirty()" placeholder="pharmacie@exemple.sn" />
              </div>
              <div class="form-group">
                <label>Ville</label>
                <input class="form-control" type="text" [(ngModel)]="form()!.city"
                       (ngModelChange)="markDirty()" placeholder="Dakar" />
              </div>
              <div class="form-group" style="grid-column: 1 / -1">
                <label>Adresse complète *</label>
                <input class="form-control" type="text" [(ngModel)]="form()!.address"
                       (ngModelChange)="markDirty()" placeholder="Rue de Thiong, Plateau" />
              </div>
            </div>
          </div>

          <!-- Coordinates -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">Localisation GPS</h3>
              <span class="material-icons" style="color:var(--color-accent)">map</span>
            </div>
            <p class="text-sm mb-md">
              Renseignez vos coordonnées GPS pour apparaître sur la carte Medoq.
            </p>
            <div class="form-grid">
              <div class="form-group">
                <label>Latitude</label>
                <input class="form-control" type="number" step="0.000001"
                       [(ngModel)]="form()!.latitude"
                       (ngModelChange)="markDirty()"
                       placeholder="14.6928" />
              </div>
              <div class="form-group">
                <label>Longitude</label>
                <input class="form-control" type="number" step="0.000001"
                       [(ngModel)]="form()!.longitude"
                       (ngModelChange)="markDirty()"
                       placeholder="-17.4467" />
              </div>
            </div>
            <div class="coords-preview">
              <span class="material-icons" style="font-size:16px;color:var(--color-primary)">location_on</span>
              <span class="text-sm">
                {{ form()!.latitude | number:'1.4-6' }}, {{ form()!.longitude | number:'1.4-6' }}
              </span>
              <a
                class="btn btn-ghost btn-sm"
                [href]="'https://www.openstreetmap.org/?mlat=' + form()!.latitude + '&mlon=' + form()!.longitude + '&zoom=16'"
                target="_blank"
                rel="noopener"
              >Voir sur OSM</a>
            </div>
          </div>

          <!-- Opening hours -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">Horaires d'ouverture</h3>
              <button class="btn btn-ghost btn-sm" (click)="fillAllDays()">
                Copier lun→tous
              </button>
            </div>
            <div class="hours-grid">
              @for (slot of daySlots; track slot.key) {
                <div class="hours-row">
                  <span class="day-label">{{ slot.label }}</span>
                  <input
                    class="form-control form-control-sm"
                    type="text"
                    [(ngModel)]="form()!.openingHours[slot.key]"
                    (ngModelChange)="markDirty()"
                    placeholder="08:00-20:00 ou Fermé"
                    style="flex:1"
                  />
                </div>
              }
            </div>
          </div>

          <!-- Stats readonly -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">Réputation</h3>
            </div>
            <div class="stats-row">
              <div class="stat-item">
                <div class="stat-value">{{ form()!.rating | number:'1.1-1' }}</div>
                <div class="stat-label">Note ⭐ / 5</div>
              </div>
              <div class="stat-item">
                <div class="stat-value">{{ form()!.reviewCount }}</div>
                <div class="stat-label">Avis clients</div>
              </div>
            </div>
            <p class="text-xs" style="margin-top:12px">
              Les avis sont laissés par les patients après chaque réservation complétée.
            </p>
          </div>

        </div>
      }
    </div>
  `,
  styles: [`
    .profile-page { max-width: 1100px; }

    .page-header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: var(--space-xl); flex-wrap: wrap; gap: var(--space-md);
    }

    .profile-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
      gap: var(--space-lg);
    }

    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-md);
    }

    /* Opening hours */
    .hours-grid { display: flex; flex-direction: column; gap: var(--space-sm); }

    .hours-row {
      display: flex; align-items: center; gap: var(--space-md);
    }

    .day-label {
      width: 90px; font-size: 13px; font-weight: 500; flex-shrink: 0;
      color: var(--color-text-secondary);
    }

    /* Coords preview */
    .coords-preview {
      display: flex; align-items: center; gap: var(--space-sm);
      background: var(--color-border-light); border-radius: var(--radius-sm);
      padding: 8px 12px; margin-top: var(--space-md);
    }

    /* Stats */
    .stats-row { display: flex; gap: var(--space-xl); }
    .stat-item { text-align: center; }
    .stat-value {
      font-size: 32px; font-weight: 700; color: var(--color-primary); line-height: 1;
    }
    .stat-label { font-size: 12px; color: var(--color-text-secondary); margin-top: 4px; }
  `],
})
export class ProfileComponent implements OnInit {
  loading = signal(true);
  saving  = signal(false);
  dirty   = signal(false);
  form    = signal<PharmacyProfile | null>(null);

  private api   = inject(ApiService);
  private auth  = inject(AuthService);
  private toast = inject(ToastService);

  readonly daySlots: DaySlot[] = [
    { key: 'monday',    label: 'Lundi',    value: '' },
    { key: 'tuesday',   label: 'Mardi',    value: '' },
    { key: 'wednesday', label: 'Mercredi', value: '' },
    { key: 'thursday',  label: 'Jeudi',    value: '' },
    { key: 'friday',    label: 'Vendredi', value: '' },
    { key: 'saturday',  label: 'Samedi',   value: '' },
    { key: 'sunday',    label: 'Dimanche', value: '' },
  ];

  ngOnInit(): void { this.load(); }

  load(): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    if (!pharmacyId) { this.loading.set(false); return; }
    this.loading.set(true);

    this.api.getProfile(pharmacyId).subscribe({
      next:  p => {
        this.form.set({ ...p, openingHours: p.openingHours ?? {} });
        this.loading.set(false);
        this.dirty.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  reset(): void {
    this.dirty.set(false);
    this.load();
  }

  markDirty(): void { this.dirty.set(true); }

  save(): void {
    const pharmacyId = (this.auth.currentUser() as any)?.['pharmacyId'];
    const profile    = this.form();
    if (!pharmacyId || !profile) return;
    this.saving.set(true);

    this.api.updateProfile(pharmacyId, profile).subscribe({
      next: updated => {
        this.form.set(updated);
        this.saving.set(false);
        this.dirty.set(false);
        this.toast.success('Profil mis à jour avec succès.');
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Erreur lors de la sauvegarde.');
      },
    });
  }

  fillAllDays(): void {
    const p = this.form();
    if (!p) return;
    const monday = p.openingHours.monday ?? '08:00-20:00';
    const days: (keyof OpeningHours)[] = [
      'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'
    ];
    days.forEach(d => { p.openingHours[d] = monday; });
    this.markDirty();
  }
}

import { Component, inject, signal, OnInit, OnDestroy, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterLink, RouterLinkActive } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../core/services/auth.service';
import { WebSocketService } from '../core/services/websocket.service';
import { ToastService } from '../core/services/toast.service';

interface NavItem {
  path:  string;
  icon:  string;
  label: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterModule, RouterLink, RouterLinkActive],
  template: `
    <div class="shell" [class.sidebar-collapsed]="collapsed()">

      <!-- ── Sidebar ─────────────────────────────────────────────── -->
      <aside class="sidebar">
        <div class="sidebar-header">
          <span class="sidebar-logo">💊</span>
          @if (!collapsed()) {
            <span class="sidebar-brand">Medoq</span>
          }
          <button class="sidebar-toggle btn btn-ghost btn-icon"
                  (click)="collapsed.update(v => !v)"
                  [title]="collapsed() ? 'Développer' : 'Réduire'">
            <span class="material-icons" style="font-size:18px">
              {{ collapsed() ? 'menu_open' : 'menu' }}
            </span>
          </button>
        </div>

        <nav class="sidebar-nav">
          @for (item of navItems; track item.path) {
            <a class="nav-item"
               [routerLink]="item.path"
               routerLinkActive="active"
               [title]="collapsed() ? item.label : ''">
              <span class="material-icons nav-icon">{{ item.icon }}</span>
              @if (!collapsed()) {
                <span class="nav-label">{{ item.label }}</span>
              }
              @if (item.path === 'reservations' && pendingCount() > 0) {
                <span class="nav-badge">{{ pendingCount() }}</span>
              }
            </a>
          }
        </nav>

        <div class="sidebar-footer">
          <a class="nav-item"
             routerLink="profile"
             routerLinkActive="active"
             [title]="collapsed() ? 'Profil' : ''">
            <span class="material-icons nav-icon">store</span>
            @if (!collapsed()) { <span class="nav-label">Ma pharmacie</span> }
          </a>
          <button class="nav-item w-full" style="border:none;background:none;cursor:pointer"
                  (click)="logout()">
            <span class="material-icons nav-icon" style="color:var(--color-error)">logout</span>
            @if (!collapsed()) {
              <span class="nav-label" style="color:var(--color-error)">Déconnexion</span>
            }
          </button>
        </div>
      </aside>

      <!-- ── Main area ───────────────────────────────────────────── -->
      <div class="main-area">

        <!-- Header -->
        <header class="topbar">
          <div class="topbar-left">
            <h2 class="topbar-title">{{ pageTitle() }}</h2>
          </div>
          <div class="topbar-right">
            <!-- Pending reservations bell -->
            <button class="topbar-btn" routerLink="reservations" title="Réservations en attente">
              <span class="material-icons">notifications</span>
              @if (pendingCount() > 0) {
                <span class="topbar-badge">{{ pendingCount() > 99 ? '99+' : pendingCount() }}</span>
              }
            </button>

            <!-- User avatar -->
            <div class="user-menu" (click)="userMenuOpen.update(v => !v)">
              <div class="user-avatar">
                {{ userInitials() }}
              </div>
              @if (!collapsed()) {
                <span class="user-name">{{ userName() }}</span>
              }
            </div>
          </div>
        </header>

        <!-- Page content -->
        <main class="page-content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: [`
    .shell {
      display: grid;
      grid-template-columns: var(--sidebar-width) 1fr;
      grid-template-rows: 1fr;
      height: 100vh;
      overflow: hidden;
      transition: grid-template-columns 0.25s ease;

      &.sidebar-collapsed {
        grid-template-columns: var(--sidebar-collapsed-width) 1fr;
      }
    }

    /* ── Sidebar ──────────────────────────────────────────────────── */

    .sidebar {
      background: var(--color-primary);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      transition: width 0.25s ease;
    }

    .sidebar-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 16px 12px;
      border-bottom: 1px solid rgba(255,255,255,.1);
      min-height: var(--header-height);

      .sidebar-logo { font-size: 24px; flex-shrink: 0; }
      .sidebar-brand {
        font-size: 20px;
        font-weight: 700;
        color: #fff;
        flex: 1;
        white-space: nowrap;
      }
      .sidebar-toggle {
        color: rgba(255,255,255,.7);
        &:hover { color: #fff; background: rgba(255,255,255,.1); }
      }
    }

    .sidebar-nav {
      flex: 1;
      padding: 8px 0;
      overflow-y: auto;
    }

    .sidebar-footer {
      padding: 8px 0;
      border-top: 1px solid rgba(255,255,255,.1);
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 16px;
      color: rgba(255,255,255,.75);
      text-decoration: none;
      border-radius: 0;
      transition: all 0.15s;
      position: relative;
      white-space: nowrap;

      &:hover { color: #fff; background: rgba(255,255,255,.08); }

      &.active {
        color: #fff;
        background: rgba(255,255,255,.15);
        border-left: 3px solid var(--color-accent);
        padding-left: 13px;

        .nav-icon { color: var(--color-accent); }
      }
    }

    .nav-icon { font-size: 20px; flex-shrink: 0; }
    .nav-label { font-size: 14px; font-weight: 500; flex: 1; }

    .nav-badge {
      min-width: 20px;
      height: 20px;
      padding: 0 6px;
      background: var(--color-error);
      color: #fff;
      border-radius: var(--radius-full);
      font-size: 11px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* ── Topbar ───────────────────────────────────────────────────── */

    .main-area {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      background: var(--color-bg);
    }

    .topbar {
      height: var(--header-height);
      background: var(--color-surface);
      border-bottom: 1px solid var(--color-border);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 var(--space-lg);
      flex-shrink: 0;
      box-shadow: var(--shadow-sm);
    }

    .topbar-title {
      font-size: 18px;
      font-weight: 600;
      color: var(--color-text-primary);
    }

    .topbar-right {
      display: flex;
      align-items: center;
      gap: var(--space-md);
    }

    .topbar-btn {
      position: relative;
      width: 40px;
      height: 40px;
      border-radius: var(--radius-md);
      border: none;
      background: var(--color-bg);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--color-text-secondary);
      transition: all 0.15s;

      &:hover { background: var(--color-border); color: var(--color-primary); }

      .material-icons { font-size: 22px; }
    }

    .topbar-badge {
      position: absolute;
      top: -4px;
      right: -4px;
      min-width: 18px;
      height: 18px;
      padding: 0 4px;
      background: var(--color-error);
      color: #fff;
      border-radius: var(--radius-full);
      font-size: 10px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .user-menu {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
      cursor: pointer;
    }

    .user-avatar {
      width: 36px;
      height: 36px;
      border-radius: var(--radius-full);
      background: var(--color-primary);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 700;
    }

    .user-name {
      font-size: 13px;
      font-weight: 500;
      color: var(--color-text-primary);
    }

    /* ── Page content ─────────────────────────────────────────────── */

    .page-content {
      flex: 1;
      overflow-y: auto;
      padding: var(--space-lg);
    }
  `],
})
export class ShellComponent implements OnInit, OnDestroy {
  collapsed    = signal(false);
  pendingCount = signal(0);
  userMenuOpen = signal(false);

  private auth   = inject(AuthService);
  private ws     = inject(WebSocketService);
  private toast  = inject(ToastService);
  private subs   = new Subscription();

  readonly navItems: NavItem[] = [
    { path: 'dashboard',    icon: 'dashboard',   label: 'Tableau de bord' },
    { path: 'reservations', icon: 'event_note',  label: 'Réservations' },
    { path: 'stock',        icon: 'inventory_2', label: 'Stock' },
    { path: 'alerts',       icon: 'notifications_active', label: 'Alertes' },
    { path: 'analytics',    icon: 'bar_chart',   label: 'Analytiques' },
  ];

  readonly pageTitle = computed(() => {
    // Derive from URL — simplified
    return 'Dashboard';
  });

  readonly userName = computed(() => {
    const u = this.auth.currentUser();
    return u ? `${u.firstName} ${u.lastName}` : '';
  });

  readonly userInitials = computed(() => {
    const u = this.auth.currentUser();
    return u ? `${u.firstName[0]}${u.lastName[0]}`.toUpperCase() : '?';
  });

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (!user) return;

    // Use a fixed pharmacyId from user metadata for now
    // (In production, fetch from /api/pharmacies?owner=me)
    const pharmacyId = (user as any)['pharmacyId'] ?? '';
    if (pharmacyId) {
      this.ws.connect(pharmacyId);
    }

    this.subs.add(
      this.ws.pendingCount$.subscribe(c => this.pendingCount.set(c))
    );

    this.subs.add(
      this.ws.reservations$.subscribe(event => {
        if (event.type === 'NEW') {
          this.pendingCount.update(c => c + 1);
          this.toast.info(
            `🔔 Nouvelle réservation — ${event.reservation.customerName} | ${event.reservation.reference}`
          );
          this._playSound();
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.ws.disconnect();
  }

  logout(): void {
    this.auth.logout();
  }

  private _playSound(): void {
    try {
      const ctx = new AudioContext();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);
      osc.start(ctx.currentTime);
      osc.stop(ctx.currentTime + 0.4);
    } catch {
      // AudioContext not available in some environments
    }
  }
}

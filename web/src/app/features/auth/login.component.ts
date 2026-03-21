import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-page">
      <div class="login-card">
        <div class="login-brand">
          <span class="login-emoji">💊</span>
          <h1>Medoq</h1>
          <p class="text-secondary">Dashboard Pharmacie</p>
        </div>

        <form (ngSubmit)="submit()" #f="ngForm">
          <div class="form-group" style="margin-bottom: 16px">
            <label>Numéro de téléphone</label>
            <input
              class="form-control"
              type="tel"
              placeholder="+221 7X XXX XX XX"
              [(ngModel)]="phone"
              name="phone"
              required
              autocomplete="username"
            />
          </div>

          <div class="form-group" style="margin-bottom: 24px">
            <label>Mot de passe</label>
            <div style="position:relative">
              <input
                class="form-control"
                [type]="showPwd() ? 'text' : 'password'"
                placeholder="••••••••"
                [(ngModel)]="password"
                name="password"
                required
                autocomplete="current-password"
                style="padding-right: 42px"
              />
              <button
                type="button"
                class="btn btn-ghost btn-icon"
                style="position:absolute;right:4px;top:50%;transform:translateY(-50%)"
                (click)="showPwd.update(v => !v)"
              >{{ showPwd() ? '🙈' : '👁️' }}</button>
            </div>
          </div>

          @if (error()) {
            <div class="login-error">❌ {{ error() }}</div>
          }

          <button
            class="btn btn-primary w-full"
            type="submit"
            [disabled]="loading()"
          >
            @if (loading()) { <span class="spinner"></span> }
            Se connecter
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .login-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
    }
    .login-card {
      background: var(--color-surface);
      border-radius: var(--radius-xl);
      padding: 40px;
      width: 100%;
      max-width: 400px;
      box-shadow: var(--shadow-lg);
    }
    .login-brand {
      text-align: center;
      margin-bottom: 32px;
      .login-emoji { font-size: 48px; display: block; margin-bottom: 8px; }
      h1 { font-size: 28px; font-weight: 700; color: var(--color-primary); }
      p  { margin-top: 4px; }
    }
    .login-error {
      background: #fee2e2;
      color: var(--color-error);
      border-radius: var(--radius-md);
      padding: 10px 14px;
      font-size: 13px;
      margin-bottom: 16px;
    }
  `],
})
export class LoginComponent {
  phone    = '';
  password = '';
  loading  = signal(false);
  error    = signal('');
  showPwd  = signal(false);

  private auth   = inject(AuthService);
  private router = inject(Router);

  submit(): void {
    if (!this.phone || !this.password) return;
    this.loading.set(true);
    this.error.set('');

    this.auth.login(this.phone, this.password).subscribe({
      next:  () => this.router.navigate(['/']),
      error: (e) => {
        this.loading.set(false);
        const status = e?.status;
        this.error.set(
          status === 401 ? 'Numéro ou mot de passe incorrect.' :
          status === 423 ? 'Compte bloqué. Réessayez dans 30 min.' :
          'Erreur de connexion. Réessayez.'
        );
      },
    });
  }
}

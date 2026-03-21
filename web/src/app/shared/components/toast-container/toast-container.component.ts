import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container">
      @for (toast of svc.toasts(); track toast.id) {
        <div class="toast toast-{{ toast.type }} animate-fade-in">
          <span class="toast-icon">{{ toast.icon }}</span>
          <span class="toast-msg">{{ toast.message }}</span>
          <span class="toast-close" (click)="svc.dismiss(toast.id)">✕</span>
        </div>
      }
    </div>
  `,
})
export class ToastContainerComponent {
  svc = inject(ToastService);
}

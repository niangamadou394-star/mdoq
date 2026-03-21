import { Injectable, signal } from '@angular/core';

export interface Toast {
  id:      number;
  type:    'success' | 'error' | 'warning' | 'info';
  message: string;
  icon:    string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private _id    = 0;
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void { this._add('success', '✅', message); }
  error(message: string):   void { this._add('error',   '❌', message); }
  warning(message: string): void { this._add('warning', '⚠️', message); }
  info(message: string):    void { this._add('info',    'ℹ️', message); }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  private _add(type: Toast['type'], icon: string, message: string): void {
    const toast: Toast = { id: ++this._id, type, icon, message };
    this.toasts.update(list => [...list, toast]);
    setTimeout(() => this.dismiss(toast.id), 4000);
  }
}

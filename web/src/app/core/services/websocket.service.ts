import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable, timer } from 'rxjs';
import { AuthService } from './auth.service';
import { WsEvent, Reservation } from '../models';

export interface ReservationEvent {
  type:        'NEW' | 'UPDATED' | 'CANCELLED' | 'EXPIRED';
  reservation: Reservation;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private ws:              WebSocket | null = null;
  private reconnectDelay = 3000;
  private _maxRetries    = 5;
  private _retries       = 0;
  private _connected     = false;

  private readonly _events$        = new Subject<WsEvent>();
  private readonly _reservations$  = new Subject<ReservationEvent>();
  private readonly _pendingCount$  = new Subject<number>();

  readonly events$       = this._events$.asObservable();
  readonly reservations$ = this._reservations$.asObservable();
  readonly pendingCount$ = this._pendingCount$.asObservable();

  constructor(private auth: AuthService) {}

  connect(pharmacyId: string): void {
    if (this._connected) return;

    const token    = this.auth.accessToken;
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const url      = `${protocol}://${location.host}/ws/pharmacy/${pharmacyId}?token=${token}`;

    this._open(url);
  }

  private _open(url: string): void {
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      this._connected = true;
      this._retries   = 0;
    };

    this.ws.onmessage = (ev) => {
      try {
        const event = JSON.parse(ev.data as string) as WsEvent;
        this._events$.next(event);

        switch (event.type) {
          case 'RESERVATION_NEW':
          case 'RESERVATION_UPDATED':
          case 'RESERVATION_CANCELLED':
          case 'RESERVATION_EXPIRED': {
            const payload = event.payload as { type: ReservationEvent['type']; reservation: Reservation };
            this._reservations$.next({
              type:        payload.type,
              reservation: payload.reservation,
            });
            break;
          }
          case 'PENDING_COUNT': {
            this._pendingCount$.next(event.payload as number);
            break;
          }
        }
      } catch {
        // Ignore malformed messages
      }
    };

    this.ws.onclose = () => {
      this._connected = false;
      if (this._retries < this._maxRetries) {
        this._retries++;
        timer(this.reconnectDelay).subscribe(() => this._open(url));
      }
    };

    this.ws.onerror = () => this.ws?.close();
  }

  disconnect(): void {
    this._connected = false;
    this._retries   = this._maxRetries; // prevent reconnect
    this.ws?.close();
  }

  ngOnDestroy(): void {
    this.disconnect();
    this._events$.complete();
    this._reservations$.complete();
    this._pendingCount$.complete();
  }
}

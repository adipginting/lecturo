import { Injectable, inject, signal } from '@angular/core';
import { ApiService, ApiError, type UserDto } from './api.service';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly api = inject(ApiService);

  private readonly current = signal<UserDto | null>(null);
  readonly user = this.current.asReadonly();

  private resolved = false;

  /** Loads the session once; returns the user if signed in. */
  async ensureLoaded(): Promise<UserDto | null> {
    if (!this.resolved) {
      try {
        this.current.set(await this.api.me());
      } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401) {
          console.error('session: could not load', error);
        }
        this.current.set(null);
      }
      this.resolved = true;
    }
    return this.current();
  }

  async login(email: string, password: string): Promise<void> {
    this.current.set(await this.api.login(email, password));
    this.resolved = true;
  }

  async register(email: string, password: string): Promise<void> {
    this.current.set(await this.api.register(email, password));
    this.resolved = true;
  }

  async logout(): Promise<void> {
    await this.api.logout();
    this.current.set(null);
  }
}

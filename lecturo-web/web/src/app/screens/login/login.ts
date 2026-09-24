import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiError } from '../../core/api.service';
import { SessionService } from '../../core/session.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
})
export class LoginScreen {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly mode = signal<'login' | 'register'>('login');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected setMode(mode: 'login' | 'register'): void {
    this.mode.set(mode);
    this.error.set(null);
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.busy()) return;

    this.busy.set(true);
    this.error.set(null);
    try {
      if (this.mode() === 'register') {
        await this.session.register(this.email(), this.password());
      } else {
        await this.session.login(this.email(), this.password());
      }
      await this.router.navigate(['/library']);
    } catch (error) {
      this.error.set(
        error instanceof ApiError ? error.message : 'Something went wrong. Try again.',
      );
    } finally {
      this.busy.set(false);
    }
  }
}

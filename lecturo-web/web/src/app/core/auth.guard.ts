import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { SessionService } from './session.service';

/** Sends unauthenticated visitors to the sign-in screen. */
export const authGuard: CanActivateFn = async () => {
  const session = inject(SessionService);
  const router = inject(Router);

  const user = await session.ensureLoaded();
  return user ? true : router.createUrlTree(['/login']);
};

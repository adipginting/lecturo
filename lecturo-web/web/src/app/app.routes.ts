import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./screens/login/login').then((m) => m.LoginScreen),
  },
  { path: '', pathMatch: 'full', redirectTo: 'library' },
  {
    path: 'library',
    canActivate: [authGuard],
    loadComponent: () => import('./screens/library/library').then((m) => m.LibraryScreen),
  },
  {
    path: 'reader/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./screens/reader/reader').then((m) => m.ReaderScreen),
  },
  {
    path: 'reader',
    canActivate: [authGuard],
    loadComponent: () => import('./screens/reader/reader').then((m) => m.ReaderScreen),
  },
  {
    path: 'chats',
    canActivate: [authGuard],
    loadComponent: () => import('./screens/chats/chats').then((m) => m.ChatsScreen),
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () => import('./screens/settings/settings').then((m) => m.SettingsScreen),
  },
  { path: '**', redirectTo: 'library' },
];

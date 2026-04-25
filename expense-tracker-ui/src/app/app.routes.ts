import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { noAuthGuard } from './core/guards/no-auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'login',
    canActivate: [noAuthGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    canActivate: [noAuthGuard],
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'transactions',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/transactions/transaction-list/transaction-list.component')
        .then(m => m.TransactionListComponent)
  },
  {
    path: 'upload',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/upload/upload.component').then(m => m.UploadComponent)
  },
  {
    path: 'calendar',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/calendar/calendar.component').then(m => m.CalendarComponent)
  },
  {
    path: 'simulator',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/simulator/simulator.component').then(m => m.SimulatorComponent)
  },
  {
    path: 'autopsy',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/autopsy/autopsy.component').then(m => m.AutopsyComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];

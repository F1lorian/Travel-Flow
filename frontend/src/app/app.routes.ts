import { Routes } from '@angular/router';
import { TripList } from './TripManagement/pages/trip-list/trip-list';
import { TripDetail } from './TripManagement/pages/trip-detail/trip-detail';
import { CashbookComponent } from './components/cashbook/cashbook.component';
import { SpendingOverviewComponent } from './components/spending-overview/spending-overview.component';
import { TripSpendingOverviewComponent } from './components/trip-spending-overview/trip-spending-overview';
import { AccountManagement } from './account-management/account-management';
import { authGuard } from './account-management/auth.guard';
import { CalendarComponent } from './components/calendar/calendar';
import { Friends } from './components/friends/friends';

export const routes: Routes = [
  { path: 'account', component: AccountManagement },
  { path: 'calendar', component: CalendarComponent, canActivate: [authGuard] },
  { path: 'trips', component: TripList, canActivate: [authGuard] },
  { path: 'trips/:id', component: TripDetail, canActivate: [authGuard] },
  { path: 'trip-spending-overview/:id', component: TripSpendingOverviewComponent, canActivate: [authGuard] },
  { path: 'cashbook', component: CashbookComponent, canActivate: [authGuard] },
  { path: 'overview', component: SpendingOverviewComponent, canActivate: [authGuard] },
  { path: 'friends', component: Friends, canActivate: [authGuard] },
  { path: '', redirectTo: 'account', pathMatch: 'full' },
  { path: '**', redirectTo: 'account' },
];

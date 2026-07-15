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
import { PrivacySettings } from './components/privacy-settings/privacy-settings';
import { GmailImport } from './components/gmail-import/gmail-import';

export const routes: Routes = [
  // No title on 'account': the login page keeps the plain "Travel Flow" tab title.
  { path: 'account', component: AccountManagement },
  { path: 'calendar', component: CalendarComponent, canActivate: [authGuard], title: 'Calendar' },
  { path: 'trips', component: TripList, canActivate: [authGuard], title: 'Trips' },
  { path: 'trips/:id', component: TripDetail, canActivate: [authGuard], title: 'Trip' },
  { path: 'trip-spending-overview/:id', component: TripSpendingOverviewComponent, canActivate: [authGuard], title: 'Trip Spending' },
  { path: 'cashbook', component: CashbookComponent, canActivate: [authGuard], title: 'Cashbook' },
  { path: 'overview', component: SpendingOverviewComponent, canActivate: [authGuard], title: 'Overview' },
  { path: 'friends', component: Friends, canActivate: [authGuard], title: 'Friends' },
  { path: 'privacy', component: PrivacySettings, canActivate: [authGuard], title: 'Privacy' },
  { path: 'gmail-import', component: GmailImport, canActivate: [authGuard], title: 'Gmail Import' },
  { path: '', redirectTo: 'account', pathMatch: 'full' },
  { path: '**', redirectTo: 'account' },

];

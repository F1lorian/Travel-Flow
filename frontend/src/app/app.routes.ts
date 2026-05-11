import { Routes } from '@angular/router';
import { TripList } from './TripManagement/pages/trip-list/trip-list';
import { TripDetail } from './TripManagement/pages/trip-detail/trip-detail';
import { TripList } from './TripManagement/pages/trip-list/trip-list';
import { TripDetail } from './TripManagement/pages/trip-detail/trip-detail';
import { CashbookComponent } from './components/cashbook/cashbook.component';
import { SpendingOverviewComponent } from './components/spending-overview/spending-overview.component';

export const routes: Routes = [
  { path: 'trips', component: TripList },
  { path: 'trips/:id', component: TripDetail },
  { path: 'cashbook', component: CashbookComponent },
  { path: 'overview', component: SpendingOverviewComponent },
  { path: '**', redirectTo: '' }
];

import { Routes } from '@angular/router';
import { TripList } from './TripManagement/pages/trip-list/trip-list';
import { TripDetail } from './TripManagement/pages/trip-detail/trip-detail';

export const routes: Routes = [
  { path: '', component: TripList },
  { path: 'trips/:id', component: TripDetail },
  { path: '**', redirectTo: '' }
];


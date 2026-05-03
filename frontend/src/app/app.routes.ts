import { Routes } from '@angular/router';
import { TripList } from './pages/trip-list/trip-list';
import { TripDetail } from './pages/trip-detail/trip-detail';

export const routes: Routes = [
  { path: '', component: TripList },
  { path: 'trips/:id', component: TripDetail },
  { path: '**', redirectTo: '' }
];


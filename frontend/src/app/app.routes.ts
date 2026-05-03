import { Routes } from '@angular/router';
import { TripList } from './pages/trip-list/trip-list';

export const routes: Routes = [
  { path: '', component: TripList },
  { path: '**', redirectTo: '' }
];


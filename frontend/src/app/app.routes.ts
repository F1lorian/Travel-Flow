import { SpendingOverviewComponent } from './components/spending-overview/spending-overview.component';
import {CashbookComponent} from './components/cashbook/cashbook.component';
import {Routes} from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    component: CashbookComponent
  },
  {
    path: 'overview',
    component: SpendingOverviewComponent
  }
];

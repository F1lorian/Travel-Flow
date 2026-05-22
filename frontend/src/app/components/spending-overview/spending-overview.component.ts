import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { CategorySummaryItem, ExpenseService } from '../../services/expense.service';
import {Router, RouterLink} from '@angular/router';
import { NgApexchartsModule } from "ng-apexcharts";
import { Trip, TripService } from '../../TripManagement/services/trip.service';

@Component({
  selector: 'app-spending-overview',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink, NgApexchartsModule],
  templateUrl: './spending-overview.component.html',
  styleUrls: ['./spending-overview.component.css']
})
export class SpendingOverviewComponent {
  startDate: string = '';
  endDate: string = '';
  expenses: any[] = [];
  showAllExpenses: boolean = false;
  categorySummary: CategorySummaryItem[] = [];
  chartseries: number[] = [];
  chartlabels: string[] = [];

  trips: Trip[] = [];

  constructor(private expenseService: ExpenseService,
              private tripService: TripService,
              private router: Router) {}

  ngOnInit() {
    // Setze Standard-Datumsbereich
    const today = new Date();
    this.endDate = today.toISOString().split('T')[0];
    const lastWeek = new Date();
    lastWeek.setDate(today.getDate() - 7);
    this.startDate = lastWeek.toISOString().split('T')[0];

    // Lade direkt beim Start
    this.loadExpenses();

    // Lade die Trips fuers drop down menu
    this.tripService.getAllTrips().subscribe({
      next: (trips) => {
        this.trips = trips;
      },
      error: (error) => {
        console.error('Fehler beim Laden der Trips:', error);
      }
    });
  }

  loadExpenses() {
    // Lade Ausgaben
    this.expenseService.getExpensesByPeriod(this.startDate, this.endDate).subscribe(
      (data: any[]) => {
        this.expenses = data;
      },
      (error) => {
        console.error('Fehler beim Laden der Ausgaben:', error);
      }
    );

    // Lade Kategorie-Auswertung
    this.expenseService.getCategorySummary(this.startDate, this.endDate).subscribe(
      (data: CategorySummaryItem[]) => {
        this.categorySummary = data;
        this.chartlabels = data.map(item => item.category);
        this.chartseries = data.map(item => item.cost);
      },
      (error) => {
        console.error('Fehler beim Laden der Kategorie-Auswertung:', error);
      }
    );
  }

  get displayedExpenses() {
    return this.showAllExpenses ? this.expenses : this.expenses.slice(0,5);
  }

  chartDetails = {
    type: 'pie' as const,
    height: 350
  };

  openTripOverview(event: Event) {
    const tripId = (event.target as HTMLSelectElement).value;

    if (!tripId) {
      return;
    }

    this.router.navigate(['/trip-spending-overview', tripId]);
  }


}

import {Component} from '@angular/core';
import {ExpenseService} from '../../services/expense.service';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {CommonModule} from '@angular/common';
import { NgApexchartsModule } from "ng-apexcharts";

@Component({
  selector: 'trip-spending-overview',
  standalone: true,
  imports: [RouterLink, CommonModule, NgApexchartsModule],
  templateUrl: './trip-spending-overview.html',
  styleUrls: ['./trip-spending-overview.css']
})

export class TripSpendingOverviewComponent {

  trip: Trip | null = null;
  tripname: string = '';

  budget: number = 0;
  summedExpenses: number = 0;
  available: number = 0;

  expenses: any[] = [];
  showAllExpenses: boolean = false;

  categorySummary: any = {};
  chartseries: number[] = [];
  chartlabels: string[] = [];

  errorMessage: string = '';

  constructor(private expenseService: ExpenseService,
              private route: ActivatedRoute,
              private tripService: TripService) {}

  ngOnInit() {
    const tripId = Number(this.route.snapshot.paramMap.get('id'));
    if(!tripId){
      this.errorMessage = "TripId fehlt";
      return;
    }
    this.tripService.getTrip(tripId).subscribe({
      next: (trip) => {
        this.trip = trip;
        this.tripname = trip.name;
        this.loadTripExpenses();
      },
      error: (error) => {
        console.error("Fehler beim Laden des Trips", error);
        this.errorMessage = "Trip konnte nicht geladen werden.";
      }
    });
    }

    loadTripExpenses() {
      this.expenseService.getBudget(this.tripname).subscribe({
        next: (data) => {
          this.budget = data;
        },
        error: (error) => {
          console.error("Fehler beim Laden des Budgets", error);
        }
      });
      this.expenseService.getSummedExpensesTrip(this.tripname).subscribe({
        next: (data) => {
          this.summedExpenses = data;
        },
        error: (error) => {
          console.error("Fehler beim Laden der Summe des Trips", error);
        }
      });
      this.expenseService.getAvailable(this.tripname).subscribe({
        next: (data) => {
          this.available = data;
        },
        error: (error) => {
          console.error("Fehler beim Laden der verfuegbaren Mitteln des Trips", error);
        }
      });
      this.expenseService.getExpensesByTrip(this.tripname).subscribe({
        next: (data) => {
          this.expenses = data;
        },
        error: (error) => {
          console.error("Fehler beim Laden der Ausgaben", error);
        }
      });
      this.expenseService.getCategorySummaryTrip(this.tripname).subscribe({
        next: (data) => {
          this.categorySummary = data;
          this.chartlabels = Object.keys(this.categorySummary);
          this.chartseries = Object.values(this.categorySummary).map(Number);
        },
        error: (error) => {
          console.error("Fehler beim Laden der kategorisierten Ausgaben", error);

        }
      })

    }

    get displayedExpenses() {
      return this.showAllExpenses ? this.expenses : this.expenses.slice(0,5);
    }

  chartDetails = {
    type: 'pie' as const,
    height: 350
  };

}

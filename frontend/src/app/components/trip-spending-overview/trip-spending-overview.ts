import {Component} from '@angular/core';
import {CategorySummaryItem, ExpenseService} from '../../services/expense.service';
import {Trip, TripService} from '../../TripManagement/services/trip.service';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {CommonModule} from '@angular/common';
import { NgApexchartsModule } from "ng-apexcharts";
import {FormsModule, ReactiveFormsModule} from '@angular/forms';

@Component({
  selector: 'trip-spending-overview',
  standalone: true,
  imports: [RouterLink, CommonModule, NgApexchartsModule, ReactiveFormsModule, FormsModule],
  templateUrl: './trip-spending-overview.html',
  styleUrls: ['./trip-spending-overview.css']
})

export class TripSpendingOverviewComponent {

  trip: Trip | null = null;
  tripname: string = '';

  budget: number = 0;
  summedExpenses: number = 0;
  available: number = 0;
  warningRange: number = 0;
  showBudgetDialog: boolean = false;
  budgetInput: number = 0;
  warningRangeInput: number = 0;
  budgetErrorMessage: string = '';
  budgetSuccessMessage: string = '';
  expenses: any[] = [];
  showAllExpenses: boolean = false;

  categorySummary: CategorySummaryItem[] = [];
  chartseries: number[] = [];
  chartlabels: string[] = [];

  errorMessage: string = '';

  trips: Trip[] = [];
  categories: string[] = [];
  selectedCategory: string = 'ALL';

  constructor(private expenseService: ExpenseService,
              private route: ActivatedRoute,
              private tripService: TripService,
              private router: Router) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
      const tripId = Number(params['id']);
      if (!tripId) {
        this.errorMessage = "TripId fehlt";
        return;
      }
      this.loadTrip(tripId);
    })
    this.tripService.getAllTrips().subscribe({
      next: (trips) => {
        this.trips = trips;
      },
      error: (error) => {
        console.error("Fehler beim Laden der Trips", error);
      }
    })
  }

    loadTrip(tripId: number) {
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
      this.expenseService.getWarningRange(this.tripname).subscribe({
        next: (data) => {
          this.warningRange = data;
        },
        error: (error) => {
          console.error("Fehler beim Laden des Warnbereichs", error);
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
          this.categories = [...new Set(data.map(expense => expense.kategorie))];
        },
        error: (error) => {
          console.error("Fehler beim Laden der Ausgaben", error);
        }
      });
      this.expenseService.getCategorySummaryTrip(this.tripname).subscribe({
        next: (data) => {
          this.categorySummary = data;
          this.chartlabels = data.map(item => item.category);
          this.chartseries = data.map(item => item.cost);
        },
        error: (error) => {
          console.error("Fehler beim Laden der kategorisierten Ausgaben", error);

        }
      })

    }

  get displayedExpenses() {
    return this.showAllExpenses
      ? this.filteredAusgaben
      : this.filteredAusgaben.slice(0, 5);
  }

  get filteredAusgaben() {
    if (this.selectedCategory === 'ALL') {
      return this.expenses;
    }

    return this.expenses.filter(expense => expense.kategorie === this.selectedCategory);
  }

  chartDetails = {
    type: 'pie' as const,
    height: 350
  };

  openOverview(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (!value) {
      return;
    }
    if(value === "interval"){
      this.router.navigate(['/overview']);
      return;
    }
    this.router.navigate(['/trip-spending-overview', value]);
  }
  openBudgetDialog(): void {
    this.budgetInput = this.budget;
    this.warningRangeInput = this.warningRange;
    this.budgetErrorMessage = '';
    this.budgetSuccessMessage = '';
    this.showBudgetDialog = true;
  }

  closeBudgetDialog(): void {
    this.showBudgetDialog = false;
    this.budgetErrorMessage = '';
  }

  saveBudget(): void {
    this.budgetErrorMessage = '';
    this.budgetSuccessMessage = '';

    if (!this.tripname) {
      this.budgetErrorMessage = 'Tripname fehlt.';
      return;
    }

    if (this.budgetInput === null || this.budgetInput === undefined || this.budgetInput < 0) {
      this.budgetErrorMessage = 'Bitte gib ein gültiges Budget ein.';
      return;
    }

    if (
      this.warningRangeInput === null ||
      this.warningRangeInput === undefined ||
      this.warningRangeInput < 0
    ) {
      this.budgetErrorMessage = 'Bitte gib einen gültigen Warnbereich ein.';
      return;
    }

    if (this.warningRangeInput > this.budgetInput) {
      this.budgetErrorMessage = 'Der Warnbereich darf nicht größer als das Budget sein.';
      return;
    }

    this.expenseService.updateBudget(this.tripname, this.budgetInput).subscribe({
      next: (updatedBudget) => {
        this.budget = updatedBudget;

        this.expenseService.updateWarningRange(this.tripname, this.warningRangeInput).subscribe({
          next: (updatedWarningRange) => {
            this.warningRange = updatedWarningRange;
            this.showBudgetDialog = false;
            this.budgetSuccessMessage = 'Budget und Warnbereich wurden aktualisiert.';

            this.loadTripExpenses();
          },
          error: (error) => {
            console.error('Fehler beim Aktualisieren des Warnbereichs:', error);
            this.budgetErrorMessage = 'Warnbereich konnte nicht gespeichert werden.';
          }
        });
      },
      error: (error) => {
        console.error('Fehler beim Aktualisieren des Budgets:', error);
        this.budgetErrorMessage = 'Budget konnte nicht gespeichert werden.';
      }
    });
  }
}

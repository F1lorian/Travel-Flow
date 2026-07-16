/* eslint-disable @angular-eslint/component-selector */
/* eslint-disable @typescript-eslint/no-inferrable-types */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @angular-eslint/prefer-inject */

import { Component, OnInit } from '@angular/core';
import {
  CategorySummaryItem,
  ExpenseForecast,
  ExpenseService
} from '../../services/expense.service';
import { Trip, TripService } from '../../TripManagement/services/trip.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { NgApexchartsModule } from 'ng-apexcharts';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { EventDialog } from '../event-dialog/event-dialog';
import { MatIcon } from '@angular/material/icon';
import { ConfirmationDialog } from '../confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'trip-spending-overview',
  standalone: true,
  imports: [RouterLink, CommonModule, NgApexchartsModule, ReactiveFormsModule, FormsModule, MatIcon],
  templateUrl: './trip-spending-overview.html',
  styleUrls: ['./trip-spending-overview.css']
})
export class TripSpendingOverviewComponent implements OnInit {

  // Currently selected trip. It is loaded based on the route parameter.
  trip: Trip | null = null;
  tripname: string = '';

  // Budget-related values displayed at the top of the page.
  budget: number = 0;
  summedExpenses: number = 0;
  available: number = 0;

  // Expense forecast for the selected trip.
  // The forecast is calculated by the backend using linear regression.
  forecast: ExpenseForecast | null = null;
  forecastLoading: boolean = false;
  forecastErrorMessage: string = '';

  // Custom warning range.
  // Interpreted as remaining-budget threshold, e.g. warn when <= 100 € remain.
  warningRange: number = 0;

  // State for the budget editing dialog.
  showBudgetDialog: boolean = false;
  budgetInput: number = 0;
  warningRangeInput: number = 0;
  budgetErrorMessage: string = '';
  budgetSuccessMessage: string = '';

  // Expense list and display state.
  expenses: any[] = [];
  showAllExpenses: boolean = false;

  // Data used for category table and ApexCharts pie chart.
  categorySummary: CategorySummaryItem[] = [];
  chartseries: number[] = [];
  chartlabels: string[] = [];

  errorMessage: string = '';

  // Available trips for switching between trip-specific overviews.
  trips: Trip[] = [];

  // Category filter for the expense table.
  categories: string[] = [];
  selectedCategory: string = 'ALL';

  // English labels for category values.
  // German keys are kept so older database rows still render cleanly.
  categoryLabels: Record<string, string> = {
    FOOD: 'Food',
    TRANSPORT: 'Transport',
    HOTEL: 'Hotel',
    LEISURE: 'Leisure',
    OTHER: 'Other',
    ESSEN: 'Food',
    FREIZEIT: 'Leisure',
    SONSTIGES: 'Other'
  };

  constructor(
    private expenseService: ExpenseService,
    private route: ActivatedRoute,
    private tripService: TripService,
    private router: Router,
    private dialog: MatDialog
  ) {}

  /**
   * Initializes the component.
   *
   * The trip id is read from the route:
   * /trip-spending-overview/:id
   *
   * After loading the selected trip, all related financial data is loaded.
   * In parallel, all trips are loaded for the trip-switch dropdown.
   */
  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const tripId = Number(params['id']);

      if (!tripId) {
        this.errorMessage = 'Trip ID is missing.';
        return;
      }

      this.loadTrip(tripId);
    });

    this.tripService.getAllTrips().subscribe({
      next: (trips) => {
        this.trips = trips;
      },
      error: (error) => {
        console.error('Error while loading trips:', error);
      }
    });
  }

  /**
   * Returns the English display label for a backend category value.
   */
  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] ?? category;
  }

  /**
   * Loads a single trip by id.
   * Once the trip is available, the component loads all related expense, budget and forecast data.
   */
  loadTrip(tripId: number): void {
    this.tripService.getTrip(tripId).subscribe({
      next: (trip) => {
        this.trip = trip;
        this.tripname = trip.name;
        this.loadTripExpenses();
        this.loadForecast();
      },
      error: (error) => {
        console.error('Error while loading the trip:', error);
        this.errorMessage = 'Trip could not be loaded.';
      }
    });
  }

  /**
   * Loads all financial information for the current trip:
   * - configured budget
   * - configured warning range
   * - total expenses
   * - available budget
   * - expense list
   * - category summary for chart and table
   */
  loadTripExpenses(): void {
    this.expenseService.getBudget(this.tripname).subscribe({
      next: (data) => {
        this.budget = data;
      },
      error: (error) => {
        console.error('Error while loading the budget:', error);
      }
    });

    this.expenseService.getWarningRange(this.tripname).subscribe({
      next: (data) => {
        this.warningRange = data;
      },
      error: (error) => {
        console.error('Error while loading the warning range:', error);
      }
    });

    this.expenseService.getSummedExpensesTrip(this.tripname).subscribe({
      next: (data) => {
        this.summedExpenses = data;
      },
      error: (error) => {
        console.error('Error while loading the trip expense sum:', error);
      }
    });

    this.expenseService.getAvailable(this.tripname).subscribe({
      next: (data) => {
        this.available = data;
      },
      error: (error) => {
        console.error('Error while loading the available budget:', error);
      }
    });

    this.expenseService.getExpensesByTrip(this.tripname).subscribe({
      next: (data) => {
        this.expenses = data;

        // Build the category filter options from the loaded expenses.
        this.categories = [...new Set(data.map(expense => expense.kategorie))];
      },
      error: (error) => {
        console.error('Error while loading expenses:', error);
      }
    });

    this.expenseService.getCategorySummaryTrip(this.tripname).subscribe({
      next: (data) => {
        this.categorySummary = data;

        // ApexCharts expects labels and series as separate arrays.
        this.chartlabels = data.map(item => this.getCategoryLabel(item.category));
        this.chartseries = data.map(item => item.cost);
      },
      error: (error) => {
        console.error('Error while loading categorized expenses:', error);
      }
    });
  }

  /**
   * Loads the expense forecast for the selected trip.
   *
   * The backend receives today's date and the current trip name.
   * It returns the predicted average daily expense and the total forecast
   * for the next 7 days.
   */
  loadForecast(): void {
    if (!this.tripname) {
      return;
    }

    this.forecastLoading = true;
    this.forecastErrorMessage = '';

    const today = new Date().toISOString().split('T')[0];

    this.expenseService.getExpenseForecast(today, this.tripname).subscribe({
      next: (forecast) => {
        this.forecast = forecast;
        this.forecastLoading = false;
      },
      error: (error) => {
        console.error('Error while loading expense forecast:', error);
        this.forecast = null;
        this.forecastLoading = false;
        this.forecastErrorMessage = 'Expense forecast could not be loaded.';
      }
    });
  }

  /**
   * Returns the expenses that should currently be displayed.
   * By default only the first five filtered expenses are shown.
   */
  get displayedExpenses() {
    return this.showAllExpenses
      ? this.filteredAusgaben
      : this.filteredAusgaben.slice(0, 5);
  }

  /**
   * Applies the selected category filter to the expense list.
   */
  get filteredAusgaben() {
    if (this.selectedCategory === 'ALL') {
      return this.expenses;
    }

    return this.expenses.filter(expense => expense.kategorie === this.selectedCategory);
  }

  // Configuration for the ApexCharts pie chart.
  chartColors = ['#0866ff', '#d4dcff', '#b49a67', '#161032', '#51a3a3'];

  chartDetails = {
    type: 'pie' as const,
    height: 350
  };

  /**
   * Handles navigation from the trip dropdown.
   *
   * If "interval" is selected, the user is redirected to the general overview.
   * Otherwise, the user is redirected to the selected trip-specific overview.
   */
  openOverview(event: Event): void {
    const value = (event.target as HTMLInputElement).value;

    if (!value) {
      return;
    }

    if (value === 'interval') {
      this.router.navigate(['/overview']);
      return;
    }

    this.router.navigate(['/trip-spending-overview', value]);
  }

  /**
   * Opens the budget dialog and initializes the input fields
   * with the currently saved budget and warning range.
   */
  openBudgetDialog(): void {
    this.budgetInput = this.budget;
    this.warningRangeInput = this.warningRange;
    this.budgetErrorMessage = '';
    this.budgetSuccessMessage = '';
    this.showBudgetDialog = true;
  }

  /**
   * Closes the budget dialog without saving changes.
   */
  closeBudgetDialog(): void {
    this.showBudgetDialog = false;
    this.budgetErrorMessage = '';
  }

  /**
   * Saves the budget and warning range for the current trip.
   *
   * Validation rules:
   * - Trip name must exist
   * - Budget must not be negative
   * - Warning range must not be negative
   * - Warning range must not be greater than the budget
   *
   * After saving, the trip data is reloaded so all displayed values are updated.
   */
  saveBudget(): void {
    this.budgetErrorMessage = '';
    this.budgetSuccessMessage = '';

    if (!this.tripname) {
      this.budgetErrorMessage = 'Trip name is missing.';
      return;
    }

    if (this.budgetInput === null || this.budgetInput === undefined || this.budgetInput < 0) {
      this.budgetErrorMessage = 'Please enter a valid budget.';
      return;
    }

    if (
      this.warningRangeInput === null ||
      this.warningRangeInput === undefined ||
      this.warningRangeInput < 0
    ) {
      this.budgetErrorMessage = 'Please enter a valid warning range.';
      return;
    }

    if (this.warningRangeInput > this.budgetInput) {
      this.budgetErrorMessage = 'The warning range must not be greater than the budget.';
      return;
    }

    /*
     * First update the budget.
     * If that succeeds, update the warning range.
     * Afterwards, reload all trip-related financial data.
     */
    this.expenseService.updateBudget(this.tripname, this.budgetInput).subscribe({
      next: (updatedBudget) => {
        this.budget = updatedBudget;

        this.expenseService.updateWarningRange(this.tripname, this.warningRangeInput).subscribe({
          next: (updatedWarningRange) => {
            this.warningRange = updatedWarningRange;
            this.showBudgetDialog = false;
            this.budgetSuccessMessage = 'Budget and warning range have been updated.';

            this.loadTripExpenses();
            this.loadForecast();
          },
          error: (error) => {
            console.error('Error while updating the warning range:', error);
            this.budgetErrorMessage = 'Warning range could not be saved.';
          }
        });
      },
      error: (error) => {
        console.error('Error while updating the budget:', error);
        this.budgetErrorMessage = 'Budget could not be saved.';
      }
    });
  }

  deleteExpense(expense: any): void {
    if (expense.sourceType === 'EVENT') {
      this.openLinkedEventPrompt(expense);
      return;
    }

    const dialogRef = this.dialog.open(ConfirmationDialog, {
      data: {
        title: 'Delete expense',
        message: 'This expense will be removed permanently.',
        confirmLabel: 'Delete expense',
        danger: true
      }
    });

    dialogRef.afterClosed().subscribe(confirmed => {
      if (!confirmed) {
        return;
      }

      this.expenseService.deleteExpense(expense.id).subscribe({
        next: () => {
          this.loadTripExpenses();
          this.loadForecast();
        },
        error: (error) => {
          console.error('Error while deleting the expense:', error);
          this.errorMessage = 'Expense could not be deleted.';
        }
      });
    });
  }

  private openLinkedEventPrompt(expense: any): void {
    const promptRef = this.dialog.open(ConfirmationDialog, {
      data: {
        title: 'Edit linked event',
        message:
          'This expense is linked to an event. To remove it from spending, edit the event and set the cost to 0, or delete the event.',
        confirmLabel: 'Edit event'
      }
    });

    promptRef.afterClosed().subscribe(confirmed => {
      if (!confirmed) {
        return;
      }

      const dialogRef = this.dialog.open(EventDialog, {
        maxHeight: '90vh',
        height: 'auto',
        data: {
          mode: 'edit',
          id: String(expense.id),
          title: expense.eventName,
          kosten: expense.kosten,
          tripId: expense.tripId,
          categoryName: expense.kategorie === 'OTHER' ? undefined : expense.kategorie,
          start: expense.start?.substring(0, 16),
          end: expense.end?.substring(0, 16),
        }
      });

      dialogRef.afterClosed().subscribe(result => {
        if (result?.action === 'saved' || result?.action === 'deleted') {
          this.loadTripExpenses();
          this.loadForecast();
        }
      });
    });
  }

  exportCsv(): void {
    if (!this.tripname) {
      return;
    }

    this.expenseService.exportExpensesByTrip(this.tripname).subscribe({
      next: (blob) => {
        this.downloadCsv(blob, `${this.tripname}-expenses.csv`);
      },
      error: (error) => {
        console.error('Error while exporting trip expenses:', error);
        this.errorMessage = 'Expenses could not be exported.';
      }
    });
  }

  private downloadCsv(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    window.URL.revokeObjectURL(url);
  }
}

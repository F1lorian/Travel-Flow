/* eslint-disable @angular-eslint/component-selector */
/* eslint-disable @typescript-eslint/no-inferrable-types */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @angular-eslint/prefer-inject */

import { Component, OnInit } from '@angular/core';
import { CategorySummaryItem, ExpenseService } from '../../services/expense.service';
import { Trip, TripService } from '../../TripManagement/services/trip.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { NgApexchartsModule } from 'ng-apexcharts';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'trip-spending-overview',
  standalone: true,
  imports: [RouterLink, CommonModule, NgApexchartsModule, ReactiveFormsModule, FormsModule],
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

  // English labels for backend category values.
  // Backend values stay unchanged, only the UI labels are translated.
  categoryLabels: Record<string, string> = {
    ESSEN: 'Food',
    TRANSPORT: 'Transport',
    HOTEL: 'Hotel',
    FREIZEIT: 'Leisure',
    SONSTIGES: 'Other'
  };

  constructor(
    private expenseService: ExpenseService,
    private route: ActivatedRoute,
    private tripService: TripService,
    private router: Router
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
   * Once the trip is available, the component loads all related expense and budget data.
   */
  loadTrip(tripId: number): void {
    this.tripService.getTrip(tripId).subscribe({
      next: (trip) => {
        this.trip = trip;
        this.tripname = trip.name;
        this.loadTripExpenses();
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
}

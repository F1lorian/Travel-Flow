import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ExpenseService } from '../../services/expense.service';
import { Trip, TripService } from '../../TripManagement/services/trip.service';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-cashbook',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './cashbook.component.html',
  styleUrls: ['./cashbook.component.css']
})
export class CashbookComponent implements OnInit {
  private readonly expenseService = inject(ExpenseService);
  private readonly tripService = inject(TripService);

  // Available categories for expenses. The values must match the seeded backend category names.
  categories = ['FOOD', 'TRANSPORT', 'HOTEL', 'LEISURE', 'OTHER'];

  // English labels for displaying category values in the UI.
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

  // Form state for creating a new expense.
  // Signals are used so the template can reactively access and update the values.
  amount = signal(0);
  tripName = signal('');
  date = signal('');
  category = signal('');

  // Trips are loaded from the backend and displayed as a dropdown.
  // This prevents users from entering invalid or non-existing trip names.
  trips = signal<Trip[]>([]);
  loadingTrips = signal(false);

  // User feedback messages shown in the template.
  errorMessage = signal('');
  successMessage = signal('');
  warningMessage = signal('');

  /**
   * Loads all existing trips when the component is initialized.
   * The trips are needed for the trip dropdown in the cashbook form.
   */
  ngOnInit(): void {
    this.loadTrips();
  }

  /**
   * Retrieves all trips from the backend.
   * Only existing trips can be selected when adding an expense.
   */
  loadTrips(): void {
    this.loadingTrips.set(true);
    this.errorMessage.set('');

    this.tripService.getAllTrips().subscribe({
      next: (trips) => {
        this.trips.set(trips);
        this.loadingTrips.set(false);
      },
      error: (error) => {
        console.error('Error while loading trips:', error);
        this.loadingTrips.set(false);
        this.errorMessage.set('Trips could not be loaded.');
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
   * Handles the complete expense creation flow.
   *
   * Steps:
   * 1. Validate the form input.
   * 2. Load the currently available budget and warning range for the selected trip.
   * 3. Warn the user if the expense exceeds the budget.
   * 4. Warn the user if the expense enters the configured warning range.
   * 5. Save the expense if the user confirms or no warning is needed.
   */
  addExpense(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.warningMessage.set('');

    const amountValue = Number(this.amount());
    const selectedTrip = this.tripName();

    // Expenses with negative values are not allowed.
    if (amountValue < 0) {
      this.errorMessage.set('Negative expenses cannot be added.');
      return;
    }

    // Reject empty, zero or invalid values.
    if (!amountValue) {
      this.errorMessage.set('Please enter a valid amount.');
      return;
    }

    if (!selectedTrip) {
      this.errorMessage.set('Please select a trip.');
      return;
    }

    if (!this.date()) {
      this.errorMessage.set('Please select a date.');
      return;
    }

    if (!this.category()) {
      this.errorMessage.set('Please select a category.');
      return;
    }

    /*
     * Load both values in parallel:
     * - available: how much budget is still available for the selected trip
     * - warningRange: custom threshold where the user wants to be warned
     *
     * forkJoin waits until both backend requests are completed.
     */
    forkJoin({
      available: this.expenseService.getAvailable(selectedTrip),
      warningRange: this.expenseService.getWarningRange(selectedTrip)
    }).subscribe({
      next: ({ available, warningRange }) => {
        const availableValue = Number(available);
        const warningRangeValue = Number(warningRange);
        const remainingAfterExpense = availableValue - amountValue;

        /*
         * Case 1: The new expense would exceed the available budget.
         * The expense is not blocked automatically because real expenses should still be recordable.
         * Instead, the user must explicitly confirm whether the expense should still be saved.
         */
        if (amountValue > availableValue) {
          const exceededBy = amountValue - availableValue;

          const shouldStillAdd = window.confirm(
            `Warning: This trip only has ${availableValue.toFixed(2)} € available.\n` +
            `This expense is ${amountValue.toFixed(2)} €.\n\n` +
            `The budget will be exceeded by ${exceededBy.toFixed(2)} €.\n\n` +
            `Add expense anyway?`
          );

          if (!shouldStillAdd) {
            this.warningMessage.set(
              'Expense was not added because the available budget is not sufficient.'
            );
            return;
          }

          this.saveExpense();
          return;
        }

        /*
         * Case 2: The expense does not exceed the budget, but it enters the warning range.
         * Example:
         * Budget warning range = 100 €
         * Remaining after expense = 80 €
         * => The user is warned because only 80 € would remain.
         */
        if (warningRangeValue > 0 && remainingAfterExpense <= warningRangeValue) {
          const shouldStillAdd = window.confirm(
            `Warning: After this expense, only ${remainingAfterExpense.toFixed(2)} € will remain.\n` +
            `This is within your warning range of ${warningRangeValue.toFixed(2)} €.\n\n` +
            `Add expense anyway?`
          );

          if (!shouldStillAdd) {
            this.warningMessage.set(
              'Expense was not added because it falls within the warning range.'
            );
            return;
          }
        }

        // No warning was needed or the user confirmed the warning.
        this.saveExpense();
      },
      error: (error) => {
        console.error('Error while checking available budget/warning range:', error);
        this.errorMessage.set('Budget and warning range could not be checked.');
      }
    });
  }

  /**
   * Sends the new expense to the backend.
   * This method is called only after validation and budget/warning checks have passed.
   */
  private saveExpense(): void {
    this.expenseService.addExpense({
      kosten: Number(this.amount()),
      trip: {
        name: this.tripName()
      },
      date: this.date(),
      kategorie: this.category()
    }).subscribe({
      next: () => {
        // Reset the form after the backend successfully stored the expense.
        this.amount.set(0);
        this.tripName.set('');
        this.date.set('');
        this.category.set('');

        this.errorMessage.set('');
        this.warningMessage.set('');
        this.successMessage.set('Expense has been added.');
      },
      error: (error) => {
        console.error('Error while saving the expense:', error);
        this.successMessage.set('');
        this.warningMessage.set('');

        if (error.status === 404) {
          this.errorMessage.set('Trip was not found. Please check the trip name.');
        } else {
          this.errorMessage.set('Expense could not be saved.');
        }
      }
    });
  }
}

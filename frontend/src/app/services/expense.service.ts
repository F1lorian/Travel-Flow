import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/**
 * Frontend representation of an expense returned by the backend.
 * The field names match the backend DTOs.
 */
export interface Expense {
  id: number;
  kosten: number;
  trip: string;
  date: string;
  kategorie: string;
  sourceType: 'EXPENSE' | 'EVENT';
  eventName?: string;
  tripId?: number;
  start?: string;
  end?: string;
}

/**
 * Represents the total cost for one category.
 * Used for tables and ApexCharts in the spending overview.
 */
export interface CategorySummaryItem {
  category: string;
  cost: number;
}

/**
 * Forecast returned by the backend for upcoming trip expenses.
 *
 * The backend currently calculates a 7-day forecast using linear regression.
 * - daily: predicted average expense per day
 * - week: predicted total expense for the next 7 days
 */
export interface ExpenseForecast {
  daily: number;
  week: number;
}

@Injectable({
  providedIn: 'root'
})
export class ExpenseService {
  private readonly http = inject(HttpClient);

  // Base path for all expense- and budget-related backend endpoints.
  // In development, this path is forwarded to the backend via the Angular proxy.
  private apiUrl = '/api';

  /**
   * Creates a new expense for a selected trip.
   *
   * The backend expects the trip as an object containing the trip name.
   * Example:
   * {
   *   kosten: 100,
   *   trip: { name: 'Paris' },
   *   date: '2026-05-20',
   *   kategorie: 'FOOD'
   * }
   */
  addExpense(expense: {
    kosten: number;
    trip: { name: string };
    date: string;
    kategorie: string;
  }): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/ausgabe`, expense);
  }

  /**
   * Deletes a manually created expense.
   */
  deleteExpense(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/ausgabe/${id}`);
  }

  /**
   * Loads all expenses within a selected date interval.
   * Used by the general spending overview.
   */
  getExpensesByPeriod(startDate: string, endDate: string): Observable<Expense[]> {
    return this.http.get<Expense[]>(
      `${this.apiUrl}/ausgabenliste?start=${startDate}&end=${endDate}`
    );
  }

  /**
   * Loads the category summary for a selected date interval.
   * The response is normalized so the component always receives an array.
   */
  getCategorySummary(startDate: string, endDate: string): Observable<CategorySummaryItem[]> {
    return this.http
      .get<CategorySummaryItem[] | Record<string, number>>(
        `${this.apiUrl}/proKategorie?start=${startDate}&end=${endDate}`
      )
      .pipe(map((response) => this.normalizeCategorySummary(response)));
  }

  /**
   * Loads all expenses for a specific trip.
   * Used by the trip-specific spending overview.
   */
  getExpensesByTrip(tripname: string): Observable<Expense[]> {
    return this.http.get<Expense[]>(
      `${this.apiUrl}/ausgabenlisteTrip?tripname=${encodeURIComponent(tripname)}`
    );
  }

  /**
   * Loads the category summary for one specific trip.
   * Used for the pie chart and category table on the trip spending overview page.
   */
  getCategorySummaryTrip(tripname: string): Observable<CategorySummaryItem[]> {
    return this.http
      .get<CategorySummaryItem[] | Record<string, number>>(
        `${this.apiUrl}/proKategorieTrip?tripname=${encodeURIComponent(tripname)}`
      )
      .pipe(map((response) => this.normalizeCategorySummary(response)));
  }

  /**
   * Loads the custom budget limit for a trip.
   */
  getBudget(tripname: string): Observable<number> {
    return this.http.get<number>(
      `${this.apiUrl}/budget?tripname=${encodeURIComponent(tripname)}`
    );
  }

  /**
   * Loads the total amount of all expenses for a trip.
   */
  getSummedExpensesTrip(tripname: string): Observable<number> {
    return this.http.get<number>(
      `${this.apiUrl}/gesamtausgabenTrip?tripname=${encodeURIComponent(tripname)}`
    );
  }

  /**
   * Loads the currently available budget for a trip.
   * This is typically calculated as: budget - summed expenses.
   */
  getAvailable(tripname: string): Observable<number> {
    return this.http.get<number>(
      `${this.apiUrl}/verfuegbar?tripname=${encodeURIComponent(tripname)}`
    );
  }

  /**
   * Loads the expense forecast for a specific trip.
   *
   * The backend uses the given date as the reference day and predicts
   * upcoming expenses for the next 7 days based on historical expenses.
   *
   * @param today current date in YYYY-MM-DD format
   * @param tripname name of the selected trip
   */
  getExpenseForecast(today: string, tripname: string): Observable<ExpenseForecast> {
    return this.http.get<ExpenseForecast>(
      `${this.apiUrl}/forecast?today=${today}&tripname=${encodeURIComponent(tripname)}`
    );
  }

  /**
   * Updates the custom budget limit for a trip.
   * Returns the updated budget value from the backend.
   */
  updateBudget(tripname: string, budget: number): Observable<number> {
    return this.http.put<number>(
      `${this.apiUrl}/budget?tripname=${encodeURIComponent(tripname)}&budget=${budget}`,
      {}
    );
  }

  /**
   * Loads the custom warning range for a trip.
   * The warning range is interpreted as a remaining-budget threshold.
   * Example: warningRange = 100 means the user is warned if only 100 € or less remain.
   */
  getWarningRange(tripname: string): Observable<number> {
    return this.http.get<number>(
      `${this.apiUrl}/warningRange?tripname=${encodeURIComponent(tripname)}`
    );
  }

  /**
   * Updates the custom warning range for a trip.
   */
  updateWarningRange(tripname: string, warningRange: number): Observable<number> {
    return this.http.put<number>(
      `${this.apiUrl}/warningRange?tripname=${encodeURIComponent(tripname)}&warningRange=${warningRange}`,
      {}
    );
  }

  /**
   * Exports expenses and event costs for a selected date interval as CSV.
   */
  exportExpensesByPeriod(startDate: string, endDate: string): Observable<Blob> {
    return this.http.get(
      `${this.apiUrl}/ausgabenliste/export?start=${startDate}&end=${endDate}`,
      { responseType: 'blob' }
    );
  }

  /**
   * Exports expenses and event costs for a selected trip as CSV.
   */
  exportExpensesByTrip(tripname: string): Observable<Blob> {
    return this.http.get(
      `${this.apiUrl}/ausgabenlisteTrip/export?tripname=${encodeURIComponent(tripname)}`,
      { responseType: 'blob' }
    );
  }

  /**
   * Normalizes category summary responses.
   *
   * The frontend expects an array of CategorySummaryItem.
   * This helper supports both possible backend response formats:
   *
   * 1. Array format:
   *    [
   *      { category: 'FOOD', cost: 100 }
   *    ]
   *
   * 2. Map/object format:
   *    {
   *      FOOD: 100
   *    }
   *
   * This makes the frontend more robust if the backend representation changes.
   */
  private normalizeCategorySummary(
    response: CategorySummaryItem[] | Record<string, number>
  ): CategorySummaryItem[] {
    if (Array.isArray(response)) {
      return response.map((item) => ({
        category: item.category,
        cost: Number(item.cost)
      }));
    }

    return Object.entries(response).map(([category, cost]) => ({
      category,
      cost: Number(cost)
    }));
  }
}

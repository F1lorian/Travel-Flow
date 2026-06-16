/* eslint-disable @typescript-eslint/no-inferrable-types */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @angular-eslint/prefer-inject */

import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { CategorySummaryItem, ExpenseService } from '../../services/expense.service';
import { Router, RouterLink } from '@angular/router';
import { NgApexchartsModule } from 'ng-apexcharts';
import { Trip, TripService } from '../../TripManagement/services/trip.service';

@Component({
  selector: 'app-spending-overview',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink, NgApexchartsModule],
  templateUrl: './spending-overview.component.html',
  styleUrls: ['./spending-overview.component.css']
})
export class SpendingOverviewComponent implements OnInit {

  // Date interval used to filter expenses in the general spending overview.
  startDate: string = '';
  endDate: string = '';

  // Expenses loaded for the selected date interval.
  expenses: any[] = [];
  showAllExpenses: boolean = false;

  // Aggregated expenses by category.
  // Used for both the category table and the pie chart.
  categorySummary: CategorySummaryItem[] = [];
  chartseries: number[] = [];
  chartlabels: string[] = [];

  // All trips are loaded for the trip filter/dropdown.
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
    private tripService: TripService,
    private router: Router
  ) {}

  /**
   * Initializes the spending overview.
   *
   * By default, the component shows expenses from the last seven days.
   * It also loads all trips so the user can switch to a trip-specific overview.
   */
  ngOnInit(): void {
    // Default interval: last seven days.
    const today = new Date();
    this.endDate = today.toISOString().split('T')[0];

    const lastWeek = new Date();
    lastWeek.setDate(today.getDate() - 7);
    this.startDate = lastWeek.toISOString().split('T')[0];

    // Load expenses and category summary for the default interval.
    this.loadExpenses();

    // Load trips for the dropdown that redirects to the trip-specific spending overview.
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
   * Loads all data for the selected date interval:
   * - individual expenses
   * - distinct categories for filtering
   * - category summary for chart and table
   *
   * This method is called on initialization and whenever the date filter changes.
   */
  loadExpenses(): void {
    this.expenseService.getExpensesByPeriod(this.startDate, this.endDate).subscribe(
      (data: any[]) => {
        this.expenses = data;

        // Build the category filter options from the loaded expenses.
        this.categories = [...new Set(data.map(expense => expense.kategorie))];
      },
      (error) => {
        console.error('Error while loading expenses:', error);
      }
    );

    this.expenseService.getCategorySummary(this.startDate, this.endDate).subscribe(
      (data: CategorySummaryItem[]) => {
        this.categorySummary = data;

        // ApexCharts requires separate arrays for labels and numeric values.
        this.chartlabels = data.map(item => this.getCategoryLabel(item.category));
        this.chartseries = data.map(item => item.cost);
      },
      (error) => {
        console.error('Error while loading category summary:', error);
      }
    );
  }

  /**
   * Returns the expenses that should currently be shown in the table.
   * By default only the first five filtered expenses are displayed.
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
   * Redirects the user to the spending overview of a selected trip.
   * The selected value is the trip id from the dropdown.
   */
  openTripOverview(event: Event): void {
    const tripId = (event.target as HTMLSelectElement).value;

    if (!tripId) {
      return;
    }

    this.router.navigate(['/trip-spending-overview', tripId]);
  }
}

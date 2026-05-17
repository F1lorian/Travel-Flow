import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ExpenseService } from '../../services/expense.service';
import {RouterLink} from '@angular/router';
import { NgApexchartsModule } from "ng-apexcharts";

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
  categorySummary: any = {};
  chartseries: number[] = [];
  chartlabels: string[] = [];

  constructor(private expenseService: ExpenseService) {}

  ngOnInit() {
    // Setze Standard-Datumsbereich
    const today = new Date();
    this.endDate = today.toISOString().split('T')[0];
    const lastWeek = new Date();
    lastWeek.setDate(today.getDate() - 7);
    this.startDate = lastWeek.toISOString().split('T')[0];

    // Lade direkt beim Start
    this.loadExpenses();
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
      (data: any) => {
        this.categorySummary = data;
        this.chartlabels = Object.keys(data);
        this.chartseries = Object.values(data).map(Number);
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

}

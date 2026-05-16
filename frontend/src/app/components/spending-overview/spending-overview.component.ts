import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ExpenseService } from '../../services/expense.service';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-spending-overview',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink],
  templateUrl: './spending-overview.component.html',
  styleUrls: ['./spending-overview.component.css']
})
export class SpendingOverviewComponent {
  startDate: string = '';
  endDate: string = '';
  expenses: any[] = [];
  showAllExpenses: boolean = false;
  categorySummary: any = {};

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
      },
      (error) => {
        console.error('Fehler beim Laden der Kategorie-Auswertung:', error);
      }
    );
  }

  get displayedExpenses() {
    return this.showAllExpenses ? this.expenses : this.expenses.slice(0,5);
  }

}

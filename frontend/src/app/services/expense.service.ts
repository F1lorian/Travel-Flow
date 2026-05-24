import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface Expense {
  kosten: number;
  trip: string;
  date: string;
  kategorie: string;
}

export interface CategorySummaryItem {
  category: string;
  cost: number;
}
type CategorySummaryResponse = Record<string, number>;

@Injectable({
  providedIn: 'root'
})
export class ExpenseService {
  private apiUrl = '/api';

  constructor(private http: HttpClient) { }

  addExpense(expense: {
    kosten: number;
    trip: { name: string };
    date: string;
    kategorie: string;
  }): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/ausgabe`, expense);
  }

  getExpensesByPeriod(startDate: string, endDate: string): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${this.apiUrl}/ausgabenliste?start=${startDate}&end=${endDate}`);
  }

  getCategorySummary(startDate: string, endDate: string): Observable<CategorySummaryItem[]> {
    return this.http
      .get<CategorySummaryResponse>(`${this.apiUrl}/proKategorie?start=${startDate}&end=${endDate}`)
      .pipe(
        map((summary) =>
          Object.entries(summary).map(([category, cost]) => ({
            category,
            cost: Number(cost)
          }))
        )
      );
  }

  getExpensesByTrip(tripname: string): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${this.apiUrl}/ausgabenlisteTrip?tripname=${tripname}`)
  }

  getCategorySummaryTrip(tripname: string): Observable<CategorySummaryItem[]> {
    return this.http
      .get<CategorySummaryResponse>(`${this.apiUrl}/proKategorieTrip?tripname=${tripname}`)
      .pipe(
        map((summary) =>
          Object.entries(summary).map(([category, cost]) => ({
            category,
            cost: Number(cost)
          }))
        )
      );
  }

  getBudget(tripname: string): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/budget?tripname=${tripname}`);
  }

  getSummedExpensesTrip(tripname: string): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/gesamtausgabenTrip?tripname=${tripname}`);
  }

  getAvailable(tripname: string): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/verfuegbar?tripname=${tripname}`);
  }

}

import type { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { map, type Observable } from "rxjs";

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

@Injectable({
	providedIn: "root",
})
export class ExpenseService {
	private apiUrl = "/api";

	constructor(private http: HttpClient) {}

	addExpense(expense: {
		kosten: number;
		trip: { name: string };
		date: string;
		kategorie: string;
	}): Observable<unknown> {
		return this.http.post(`${this.apiUrl}/ausgabe`, expense);
	}

	getExpensesByPeriod(
		startDate: string,
		endDate: string,
	): Observable<Expense[]> {
		return this.http.get<Expense[]>(
			`${this.apiUrl}/ausgabenliste?start=${startDate}&end=${endDate}`,
		);
	}

	getCategorySummary(
		startDate: string,
		endDate: string,
	): Observable<CategorySummaryItem[]> {
		return this.http
			.get<CategorySummaryItem[]>(
				`${this.apiUrl}/proKategorie?start=${startDate}&end=${endDate}`,
			)
			.pipe(
				map((items) =>
					items.map((item) => ({
						category: item.category,
						cost: Number(item.cost),
					})),
				),
			);
	}

	getExpensesByTrip(tripname: string): Observable<Expense[]> {
		return this.http.get<Expense[]>(
			`${this.apiUrl}/ausgabenlisteTrip?tripname=${tripname}`,
		);
	}

	getCategorySummaryTrip(tripname: string): Observable<CategorySummaryItem[]> {
		return this.http
			.get<CategorySummaryItem[]>(
				`${this.apiUrl}/proKategorieTrip?tripname=${encodeURIComponent(tripname)}`,
			)
			.pipe(
				map((items) =>
					items.map((item) => ({
						category: item.category,
						cost: Number(item.cost),
					})),
				),
			);
	}

	getBudget(tripname: string): Observable<number> {
		return this.http.get<number>(`${this.apiUrl}/budget?tripname=${tripname}`);
	}

	getSummedExpensesTrip(tripname: string): Observable<number> {
		return this.http.get<number>(
			`${this.apiUrl}/gesamtausgabenTrip?tripname=${tripname}`,
		);
	}

	getAvailable(tripname: string): Observable<number> {
		return this.http.get<number>(
			`${this.apiUrl}/verfuegbar?tripname=${encodeURIComponent(tripname)}`,
		);
	}

	updateBudget(tripname: string, budget: number): Observable<number> {
		return this.http.put<number>(
			`${this.apiUrl}/budget?tripname=${encodeURIComponent(tripname)}&budget=${budget}`,
			{},
		);
	}

	getWarningRange(tripname: string): Observable<number> {
		return this.http.get<number>(
			`${this.apiUrl}/warningRange?tripname=${encodeURIComponent(tripname)}`,
		);
	}

	updateWarningRange(
		tripname: string,
		warningRange: number,
	): Observable<number> {
		return this.http.put<number>(
			`${this.apiUrl}/warningRange?tripname=${encodeURIComponent(tripname)}&warningRange=${warningRange}`,
			{},
		);
	}

	private normalizeCategorySummary(
		response: CategorySummaryItem[] | Record<string, number>,
	): CategorySummaryItem[] {
		if (Array.isArray(response)) {
			return response.map((item) => ({
				category: item.category,
				cost: Number(item.cost),
			}));
		}

		return Object.entries(response).map(([category, cost]) => ({
			category,
			cost: Number(cost),
		}));
	}
}

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

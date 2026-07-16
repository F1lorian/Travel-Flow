export interface CalendarEventImport {
  id: string;
  title: string;
  start: string;
  end: string;
  kosten: number;
  tripId: number;
  categoryName?: string;
  creatorUsername?: string;
}

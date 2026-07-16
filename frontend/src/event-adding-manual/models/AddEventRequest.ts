export interface AddEventRequest {
  name: string;
  kosten: number;
  tripId?: number | undefined;
  categoryName?: string | null;
  start: string;
  end: string;
}

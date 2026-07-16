export interface EditEventRequest {
  id: string;
  name: string;
  kosten: number;
  tripId?: number | undefined;
  categoryName?: string | null;
  start: string;
  end: string;
}

export interface EditEventResponse {
  id: string;
  name: string;
  kosten: number;
  tripId: number;
  categoryName?: string;
  start: string;
  end: string;
}

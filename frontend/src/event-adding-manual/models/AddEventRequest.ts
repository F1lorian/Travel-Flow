export interface AddEventRequest {
  name: string;
  kosten: number;
  tripId?: number | undefined;
  start: string;
  end: string;
}

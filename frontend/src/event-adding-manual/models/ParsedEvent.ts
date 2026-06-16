/**
 * Event values extracted from an imported .eml file before the event is saved.
 */
export interface ParsedEvent {
  name: string;
  kosten: number;
  start: string;
  end: string;
}

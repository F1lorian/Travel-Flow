import {Component, inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {ParsedEvent} from '../../../event-adding-manual/models/ParsedEvent';

/**
 * Lets the user double-check and correct the event values extracted from an
 * imported .eml file before the event is saved. Closes with the edited
 * {@link ParsedEvent} when confirmed, or with undefined when cancelled.
 */
@Component({
  selector: 'app-event-preview-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './event-preview-dialog.html',
  styleUrl: './event-preview-dialog.css',
})
export class EventPreviewDialog {
  private readonly dialogRef = inject(MatDialogRef<EventPreviewDialog>);
  readonly data = inject<{ parsedEvent: ParsedEvent }>(MAT_DIALOG_DATA);

  name = this.data.parsedEvent.name;
  kosten = this.data.parsedEvent.kosten;
  start = this.toInputValue(this.data.parsedEvent.start);
  end = this.toInputValue(this.data.parsedEvent.end);

  /**
   * Returns the edited event values to the caller so they can be saved.
   */
  confirm() {
    this.dialogRef.close({
      name: this.name,
      kosten: this.kosten,
      start: this.fromInputValue(this.start),
      end: this.fromInputValue(this.end),
    });
  }

  /**
   * Closes the dialog without saving the imported event.
   */
  cancel() {
    this.dialogRef.close();
  }

  /**
   * Converts a backend date-time string to the value expected by a
   * datetime-local input (yyyy-MM-ddTHH:mm).
   * @param value date-time string from the parsed event
   */
  private toInputValue(value: string): string {
    return value ? value.slice(0, 16) : '';
  }

  /**
   * Converts a datetime-local input value back to the yyyy-MM-ddTHH:mm:ss
   * format used by the backend.
   * @param value value taken from a datetime-local input
   */
  private fromInputValue(value: string): string {
    if (!value) {
      return value;
    }
    return value.length === 16 ? `${value}:00` : value;
  }
}

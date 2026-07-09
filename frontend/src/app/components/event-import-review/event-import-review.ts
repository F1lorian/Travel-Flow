import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ParsedEvent } from '../../../event-adding-manual/models/ParsedEvent';

/** A parsed event together with the subject of the message it came from. */
export interface ReviewItem {
  subject: string;
  event: ParsedEvent;
}

interface EventImportReviewData {
  items: ReviewItem[];
}

/**
 * Lets the user review the events parsed from several messages and pick which ones to actually
 * import. Closes with the list of selected {@link ParsedEvent}s, or `undefined` when cancelled.
 */
@Component({
  selector: 'app-event-import-review',
  standalone: true,
  templateUrl: './event-import-review.html',
})
export class EventImportReview {
  private readonly dialogRef = inject(MatDialogRef<EventImportReview>);
  readonly data = inject<EventImportReviewData>(MAT_DIALOG_DATA);

  rows = this.data.items.map((item) => ({ ...item, selected: true }));

  get selectedCount(): number {
    return this.rows.filter((row) => row.selected).length;
  }

  toggle(row: { selected: boolean }) {
    row.selected = !row.selected;
  }

  confirm() {
    this.dialogRef.close(this.rows.filter((row) => row.selected).map((row) => row.event));
  }

  cancel() {
    this.dialogRef.close(undefined);
  }

  formatDateTime(value: string): string {
    return new Intl.DateTimeFormat('de-DE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }
}

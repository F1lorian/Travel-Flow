import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {AddEventResponse} from '../../../event-adding-manual/models/AddEventResponse';

@Component({
  selector: 'app-conflict-dialog',
  standalone: true,
  templateUrl: './conflict-dialog.html',
  styleUrl: './conflict-dialog.css',
})
export class ConflictDialog {
  conflicts: AddEventResponse[] = [];

  constructor(
    private dialogRef: MatDialogRef<ConflictDialog>,
    @Inject(MAT_DIALOG_DATA) public data: { conflicts: AddEventResponse[] }
  ) {
    this.conflicts = data.conflicts;
  }

  confirm() {
    this.dialogRef.close(true);
  }

  cancel() {
    this.dialogRef.close(false);
  }

  formatDateTime(value: string) {
    return new Intl.DateTimeFormat('de-DE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value));
  }
}

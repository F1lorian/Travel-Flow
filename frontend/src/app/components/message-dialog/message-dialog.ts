import {Component, inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';

interface MessageDialogData {
  message: string;
}

@Component({
  selector: 'app-error-dialog',
  imports: [FormsModule],
  templateUrl: './message-dialog.html',
  styleUrl: './message-dialog.css',
})
export class MessageDialog {
  private readonly dialogRef = inject(MatDialogRef<MessageDialog>);
  readonly data = inject<MessageDialogData>(MAT_DIALOG_DATA);
  message = '';

  constructor() {
    if (this.data) {
      this.message = this.data.message;
    }
  }

  close(){
    this.dialogRef.close();
  }
}


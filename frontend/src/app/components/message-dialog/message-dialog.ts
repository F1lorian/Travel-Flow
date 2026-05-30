import {Component, Inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-error-dialog',
  imports: [FormsModule],
  templateUrl: './message-dialog.html',
  styleUrl: './message-dialog.css',
})
export class MessageDialog {
  message: string = '';

  //creates pop up window for error messages
  constructor(private dialogRef: MatDialogRef<MessageDialog>, @Inject(MAT_DIALOG_DATA) public data: any) {
    if (data) {
      this.message = data.message;
    }
  }

  //closes
  close(){
    this.dialogRef.close();
  }
}


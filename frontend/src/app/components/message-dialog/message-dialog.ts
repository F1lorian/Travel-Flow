import { Component, Inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MAT_DIALOG_DATA, type MatDialogRef } from "@angular/material/dialog";

@Component({
	selector: "app-error-dialog",
	imports: [FormsModule],
	templateUrl: "./message-dialog.html",
	styleUrl: "./message-dialog.css",
})
export class MessageDialog {
	message: string = "";

	constructor(private dialogRef: MatDialogRef<MessageDialog>, @Inject(MAT_DIALOG_DATA) public data: any) {
    if (data) {
      this.message = data.message;
    }
  }

	close() {
		this.dialogRef.close();
	}
}

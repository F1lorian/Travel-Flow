import { type ComponentFixture, TestBed } from "@angular/core/testing";

import { Calendar } from "@fullcalendar/core";

describe("Calendar", () => {
	let component: Calendar;
	let fixture: ComponentFixture<Calendar>;

	beforeEach(async () => {
		await TestBed.configureTestingModule({
			imports: [Calendar],
		}).compileComponents();

		fixture = TestBed.createComponent(Calendar);
		component = fixture.componentInstance;
		fixture.detectChanges();
	});

	it("should create", () => {
		expect(component).toBeTruthy();
	});
});

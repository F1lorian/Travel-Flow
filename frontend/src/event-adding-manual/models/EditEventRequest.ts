export type EditEventRequest = {
	id: string;
	name: string;
	kosten: number;
	tripId?: number | undefined;
	start: string;
	end: string;
};

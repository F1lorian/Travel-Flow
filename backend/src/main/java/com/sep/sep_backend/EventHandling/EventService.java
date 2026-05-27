package com.sep.sep_backend.EventHandling;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.AusgabeRequestDTO;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.sep.sep_backend.ausgabe.Kategorie;


import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TripRepository tripRepository;
    private final AusgabeService ausgabeService;


    @Autowired
    public EventService(EventRepository eventRepository, TripRepository triprepository, AusgabeService ausgabeService) {
        this.eventRepository = eventRepository;
        this.tripRepository = triprepository;
        this.ausgabeService = ausgabeService;
    }


    //Todo Name bei Trip muss besser gelöst werden jetzt vereinfacht über id
    public EventResponseDTO SaveEventToDB(EventRequestDTO dto)
    {
        Trip matchingTrip = tripRepository.findById(dto.getTripId()).orElseThrow(() -> new RuntimeException("Zu dieser trip Id findet sich kein eintrag in der Datenbank"));
        LocalDateTime eventStart = dto.getStart() != null ? dto.getStart() : LocalDateTime.now();
        LocalDateTime eventEnd = dto.getEnd() != null ? dto.getEnd() : LocalDateTime.now().plusDays(1);
        Event newEvent = new Event(dto.getName(), eventStart, eventEnd, dto.getKosten(), matchingTrip);
        EventResponseDTO ausgabe = new EventResponseDTO(dto.getName(), dto.getKosten(), eventStart, eventEnd);
        eventRepository.save(newEvent);

        // add the expence automatically to the db
        AusgabeRequestDTO ausgabeRequestDTO = new AusgabeRequestDTO( (double) dto.getKosten(), matchingTrip, eventStart.toLocalDate(), Kategorie.SONSTIGES);
        ausgabeService.ausgabeHinzufuegen(ausgabeRequestDTO);

        return ausgabe;
    }
    public List<EventCalendarDTO> GetAllEventsFromDb()
    {
        return eventRepository.findAll()
                .stream()
                .map(event -> new EventCalendarDTO(
                        String.valueOf(event.getId()),
                        event.getName(),
                        event.getKosten(),
                        event.getStartTime(),
                        event.getEndTime(),
                        event.getTripId()
                ))
                .toList();
    }

}

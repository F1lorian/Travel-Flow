package com.sep.sep_backend.EventHandling;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TripRepository tripRepository;


    @Autowired
    public EventService(EventRepository eventRepository, TripRepository triprepository) {
        this.eventRepository = eventRepository;
        this.tripRepository = triprepository;
    }


    //Todo Name bei Trip muss besser gelöst werden jetzt vereinfacht über id
    public EventResponseDTO SaveEventToDB(EventRequestDTO dto)
    {
        Trip matchingTrip = tripRepository.findById(dto.getTripId()).orElseThrow(() -> new RuntimeException("Zu dieser trip Id findet sich kein eintrag in der Datenbank"));
        Event newEvent = new Event(dto.getName(), LocalDateTime.now(), dto.getKosten(), matchingTrip);
        EventResponseDTO ausgabe = new EventResponseDTO(dto.getName(),dto.getKosten(), LocalDateTime.now());
        eventRepository.save(newEvent);
        return ausgabe;
    }
    public List<EventCalendarDTO> GetAllEventsFromDb()
    {
        return eventRepository.findAll()
                .stream()
                .map(event -> new EventCalendarDTO(
                        String.valueOf(event.getId()),
                        event.getName(),
                        event.getDateTime()
                ))
                .toList();
    }

}

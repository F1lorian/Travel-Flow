package com.sep.sep_backend.ausgaben;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.AusgabeRequestDTO;
import com.sep.sep_backend.ausgabe.AusgabeResponseDTO;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.ausgabe.Kategorie;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class AusgabenServiceTest {

    @Test
    void ausgabeHinzufuegenSpeichertAusgabeMitExistierendemTrip(){
        // du erstellst einen Service
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        TripRepository tripRepository = mock(TripRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        AusgabeService ausgabeService = new AusgabeService(ausgabeRepository, tripRepository,  eventRepository);

    // du erstellst 2 Trips, die den gleichen Trip repraesentieren muessen, und "speicherst" den
    // einen
    Trip existingTrip = new Trip();
    existingTrip.setName("Berlin");
    Trip requestedTrip = new Trip();
    requestedTrip.setName("Berlin");

    when(tripRepository.findByName("Berlin")).thenReturn(existingTrip);

    // du fuegst eine Ausgabe hinzu
    AusgabeRequestDTO ausgabeDTO =
        new AusgabeRequestDTO(10, requestedTrip, LocalDate.of(2026, 5, 10), Kategorie.HOTEL);
    ausgabeService.ausgabeHinzufuegen(ausgabeDTO);

    // vergleichst ob das richtige gespeichert wurde
    ArgumentCaptor<Ausgabe> captor = ArgumentCaptor.forClass(Ausgabe.class);
    verify(ausgabeRepository).save(captor.capture());

    Ausgabe saved = captor.getValue();
    assertThat(saved.getKosten()).isEqualTo(10);
    assertThat(saved.getTrip()).isSameAs(existingTrip);
    assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 5, 10));
    assertThat(saved.getKategorie()).isEqualTo(Kategorie.HOTEL);

    // pruefst ob der reuested Trip tatsaechlich nie gespeichert wurde
    verify(tripRepository).findByName("Berlin");
    verify(tripRepository, never()).save(requestedTrip);
  }

    @Test
    void ausgabenlisteGibtDTOsZurueck(){
        // du erstellst einen Service
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        TripRepository tripRepository = mock(TripRepository.class);
        EventRepository eventRepository =  mock(EventRepository.class);
        AusgabeService ausgabeService = new AusgabeService(ausgabeRepository, tripRepository, eventRepository);

    // du "speicherst" eine Ausgabe und holst sie dir
    Trip trip = new Trip();
    trip.setName("Duisburg");

    Ausgabe ausgabe = new Ausgabe(200, trip, LocalDate.of(2026, 5, 9), Kategorie.HOTEL);
    when(ausgabeRepository.findByDateBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)))
        .thenReturn(List.of(ausgabe));

    List<AusgabeResponseDTO> result =
        ausgabeService.ausgabenliste(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10));

    // pruefen ob die zurueckgegebene Liste, den richtigen Inhalt hat
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKosten()).isEqualTo(200);
    assertThat(result.get(0).getTrip()).isEqualTo("Duisburg");
    assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 9));
    assertThat(result.get(0).getKategorie()).isEqualTo(Kategorie.HOTEL);
  }

    @Test
    void ausgabeProKategorieSummiertKosten(){
        // du erstellst einen Service
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        TripRepository tripRepository = mock(TripRepository.class);
        EventRepository eventRepository =  mock(EventRepository.class);
        AusgabeService ausgabeService = new  AusgabeService(ausgabeRepository, tripRepository, eventRepository);

    // "speicherst" 2 Ausgaben in derselben Kategorie
    Trip trip1 = new Trip();
    trip1.setName("Paris");
    Ausgabe ausgabe1 = new Ausgabe(6767, trip1, LocalDate.of(2026, 5, 11), Kategorie.SONSTIGES);

    Trip trip2 = new Trip();
    trip2.setName("Barcelona");
    Ausgabe ausgabe2 = new Ausgabe(67, trip2, LocalDate.of(2026, 5, 11), Kategorie.SONSTIGES);

    when(ausgabeRepository.findByDateBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 11)))
        .thenReturn(List.of(ausgabe1, ausgabe2));

    // fuehrst die Methode aus
    Map<Kategorie, Double> result =
        ausgabeService.ausgabenProKategorie(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 11));

    // pruefst ob er die Kosten zusammengerechnet hat
    assertThat(result.get(Kategorie.SONSTIGES)).isEqualTo(6834);
  }
}

package com.sep.sep_backend.ausgaben;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.AusgabeRequestDTO;
import com.sep.sep_backend.ausgabe.AusgabeResponseDTO;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.ausgabe.KategorieSummeDTO;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.CategoriesRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class AusgabenServiceTest {

  @Test
  void ausgabeHinzufuegenSpeichertAusgabeMitAngefragtemTrip() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository, tripRepository, eventRepository, categoriesRepository);

    Trip requestedTrip = new Trip();
    requestedTrip.setName("Berlin");
    Categories category = new Categories("Freizeit");

    when(categoriesRepository.findByName("Freizeit")).thenReturn(Optional.of(category));

    // du fuegst eine Ausgabe hinzu
    AusgabeRequestDTO ausgabeDTO =
        new AusgabeRequestDTO(10, requestedTrip, LocalDate.of(2026, 5, 10), "Freizeit");
    ausgabeService.ausgabeHinzufuegen(ausgabeDTO);

    // vergleichst ob das richtige gespeichert wurde
    ArgumentCaptor<Ausgabe> captor = ArgumentCaptor.forClass(Ausgabe.class);
    verify(ausgabeRepository).save(captor.capture());

    Ausgabe saved = captor.getValue();
    assertThat(saved.getKosten()).isEqualTo(10);
    assertThat(saved.getTrip()).isSameAs(requestedTrip);
    assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 5, 10));
    assertThat(saved.getKategorie()).isSameAs(category);

    verify(categoriesRepository).findByName("Freizeit");
    verifyNoInteractions(tripRepository);
  }

  @Test
  void ausgabenlisteGibtDTOsZurueck() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository, tripRepository, eventRepository, categoriesRepository);

    // du "speicherst" eine Ausgabe und holst sie dir
    Trip trip = new Trip();
    trip.setName("Duisburg");

    Ausgabe ausgabe = new Ausgabe(200, trip, LocalDate.of(2026, 5, 9), new Categories());
    when(ausgabeRepository.findByDateBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)))
        .thenReturn(List.of(ausgabe));
    when(eventRepository.findByStartTimeBetween(
            LocalDate.of(2026, 5, 1).atStartOfDay(), LocalDate.of(2026, 5, 10).atStartOfDay()))
        .thenReturn(List.of());

    List<AusgabeResponseDTO> result =
        ausgabeService.ausgabenliste(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10));

    // pruefen ob die zurueckgegebene Liste, den richtigen Inhalt hat
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKosten()).isEqualTo(200);
    assertThat(result.get(0).getTrip()).isEqualTo("Duisburg");
    assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 9));
  }

  @Test
  void ausgabeProKategorieSummiertKosten() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository, tripRepository, eventRepository, categoriesRepository);

    // "speicherst" 2 Ausgaben in derselben Kategorie
    Trip trip1 = new Trip();
    trip1.setName("Paris");
    Ausgabe ausgabe1 =
        new Ausgabe(6767, trip1, LocalDate.of(2026, 5, 11), new Categories("Freizeit"));

    Trip trip2 = new Trip();
    trip2.setName("Barcelona");
    Ausgabe ausgabe2 =
        new Ausgabe(67, trip2, LocalDate.of(2026, 5, 11), new Categories("Freizeit"));

    when(ausgabeRepository.findByDateBetween(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 11)))
        .thenReturn(List.of(ausgabe1, ausgabe2));
    when(eventRepository.findByStartTimeBetween(
            LocalDate.of(2026, 5, 1).atStartOfDay(), LocalDate.of(2026, 5, 11).atStartOfDay()))
        .thenReturn(List.of());

    // fuehrst die Methode aus
    List<KategorieSummeDTO> result =
        ausgabeService.ausgabenProKategorie(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 11));

    // pruefst ob er die Kosten zusammengerechnet hat
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCost()).isEqualTo(6834);
  }

  @Test
  void ausgabenlisteTripGibtDTOsZurueck() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository, tripRepository, eventRepository, categoriesRepository);

    Trip trip = new Trip("London");
    Ausgabe y =
        new Ausgabe(100, new Trip("London"), LocalDate.of(2026, 1, 1), new Categories("Freizeit"));

    when(ausgabeRepository.findByTrip(trip)).thenReturn(List.of(y));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(eventRepository.findByTrip_Id(trip.getId())).thenReturn(List.of());

    // die methode ausfuehren
    List<AusgabeResponseDTO> result = ausgabeService.ausgabenlisteTrip("London");

    // vergleichen
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKosten()).isEqualTo(100);
    assertThat(result.get(0).getTrip()).isEqualTo("London");
    assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 1));
  }

  @Test
  void ausgabenProKategorieTripSummiertAusgabenUndEvents() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository, tripRepository, eventRepository, categoriesRepository);

    Trip trip = new Trip("London");
    trip.setId(1);

    Ausgabe ausgabe1 = new Ausgabe(100, trip, LocalDate.of(2026, 1, 1), new Categories("Freizeit"));
    Ausgabe ausgabe2 = new Ausgabe(50, trip, LocalDate.of(2026, 1, 2), new Categories("Essen"));
    Event event =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 1, 3, 11, 0),
            LocalDateTime.of(2026, 1, 3, 12, 0),
            25,
            trip);
    event.setCategorie(new Categories("Freizeit"));

    when(tripRepository.findByName("London")).thenReturn(trip);
    when(ausgabeRepository.findByTrip(trip)).thenReturn(List.of(ausgabe1, ausgabe2));
    when(eventRepository.findByTrip_Id(trip.getId())).thenReturn(List.of(event));

    // fuehrst die Methode aus
    List<KategorieSummeDTO> result = ausgabeService.ausgabenProKategorieTrip("London");

    // pruefst ob die Kosten pro Kategorie zusammengerechnet wurden
    assertThat(result).hasSize(2);
    assertThat(result)
        .anySatisfy(
            kategorie -> {
              assertThat(kategorie.getCategory()).isEqualTo("Freizeit");
              assertThat(kategorie.getCost()).isEqualTo(125);
            });
    assertThat(result)
        .anySatisfy(
            kategorie -> {
              assertThat(kategorie.getCategory()).isEqualTo("Essen");
              assertThat(kategorie.getCost()).isEqualTo(50);
            });
  }

  @Test
  void gesamtausgabenTripSummiertAusgabenUndEvents() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository, tripRepository, eventRepository, categoriesRepository);

    Trip trip = new Trip("London");
    trip.setId(1);
    Ausgabe ausgabe = new Ausgabe(100, trip, LocalDate.of(2026, 1, 1), new Categories("Freizeit"));
    Event event =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 1, 3, 11, 0),
            LocalDateTime.of(2026, 1, 3, 12, 0),
            25,
            trip);

    when(tripRepository.findByName("London")).thenReturn(trip);
    when(ausgabeRepository.findByTrip(trip)).thenReturn(List.of(ausgabe));
    when(eventRepository.findByTrip_Id(trip.getId())).thenReturn(List.of(event));

    // fuehrst die Methode aus
    double result = ausgabeService.gesamtausgabenTrip("London");

    // pruefst ob Ausgaben und Events zusammengerechnet wurden
    assertThat(result).isEqualTo(125);
  }

  @Test
  void verfuegbarTripGibtBudgetMinusGesamtausgabenZurueck() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository, tripRepository, eventRepository, categoriesRepository);

    Trip trip = new Trip("London");
    trip.setId(1);
    trip.setBudget(300);
    Ausgabe ausgabe = new Ausgabe(100, trip, LocalDate.of(2026, 1, 1), new Categories("Freizeit"));
    Event event =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 1, 3, 11, 0),
            LocalDateTime.of(2026, 1, 3, 12, 0),
            25,
            trip);

    when(tripRepository.findByName("London")).thenReturn(trip);
    when(ausgabeRepository.findByTrip(trip)).thenReturn(List.of(ausgabe));
    when(eventRepository.findByTrip_Id(trip.getId())).thenReturn(List.of(event));

    // fuehrst die Methode aus
    double result = ausgabeService.verfuegbarTrip("London");

    // pruefst ob Budget minus Gesamtausgaben zurueckgegeben wird
    assertThat(result).isEqualTo(175);
  }
}

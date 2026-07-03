package com.sep.sep_backend.ausgaben;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.ausgabe.AusgabeRequestDTO;
import com.sep.sep_backend.ausgabe.AusgabeResponseDTO;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.ausgabe.KategorieSummeDTO;
import com.sep.sep_backend.entities.Ausgabe;
import com.sep.sep_backend.entities.Categories;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.CategoriesRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

public class AusgabenServiceTest {

  @Test
  void ausgabeHinzufuegenSpeichertAusgabeMitAngefragtemTrip() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    UserTripRepository userTripRepository = mock(UserTripRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository,
            userTripRepository);

    Trip requestedTrip = new Trip();
    requestedTrip.setName("Berlin");
    Categories category = new Categories("Freizeit");
    User user = new User("maxmuster", "max@example.com", "hash", "answer");
    UserTrip userTrip = new UserTrip(user, requestedTrip, null);

    when(categoriesRepository.findByName("Freizeit")).thenReturn(Optional.of(category));
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(userTripRepository.findByUserAndTrip(user, requestedTrip))
        .thenReturn(Optional.of(userTrip));

    // du fuegst eine Ausgabe hinzu
    AusgabeRequestDTO ausgabeDTO =
        new AusgabeRequestDTO(10, requestedTrip, LocalDate.of(2026, 5, 10), "Freizeit");
    ausgabeService.ausgabeHinzufuegen(ausgabeDTO, "maxmuster");

    // vergleichst ob das richtige gespeichert wurde
    ArgumentCaptor<Ausgabe> captor = ArgumentCaptor.forClass(Ausgabe.class);
    verify(ausgabeRepository).save(captor.capture());

    Ausgabe saved = captor.getValue();
    assertThat(saved.getKosten()).isEqualTo(10);
    assertThat(saved.getTrip()).isSameAs(requestedTrip);
    assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 5, 10));
    assertThat(saved.getKategorie()).isSameAs(category);
    assertThat(saved.getUser()).isSameAs(user);

    verify(categoriesRepository).findByName("Freizeit");
    verify(userRepository).findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster");
    verifyNoInteractions(tripRepository);
  }

  @Test
  void ausgabenlisteGibtDTOsZurueck() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

    // du "speicherst" eine Ausgabe und holst sie dir
    Trip trip = new Trip();
    trip.setId(7);
    trip.setName("Duisburg");

    User user = new User("maxmuster", "max@example.com", "hash", "answer");
    Ausgabe ausgabe = new Ausgabe(200, trip, LocalDate.of(2026, 5, 9), new Categories());
    ausgabe.setId(42);
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(ausgabeRepository.findByUserAndDateBetween(
            user, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)))
        .thenReturn(List.of(ausgabe));
    when(eventRepository.findByUsersContainingAndStartTimeBetween(
            user,
            LocalDate.of(2026, 5, 1).atStartOfDay(),
            LocalDate.of(2026, 5, 10).atStartOfDay()))
        .thenReturn(List.of());

    List<AusgabeResponseDTO> result =
        ausgabeService.ausgabenliste(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10), "maxmuster");

    // pruefen ob die zurueckgegebene Liste, den richtigen Inhalt hat
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKosten()).isEqualTo(200);
    assertThat(result.get(0).getTrip()).isEqualTo("Duisburg");
    assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 9));
    assertThat(result.get(0).getId()).isEqualTo(42);
    assertThat(result.get(0).getSourceType()).isEqualTo("EXPENSE");
    assertThat(result.get(0).getTripId()).isEqualTo(7);
  }

  @Test
  void ausgabenlisteGibtEventDTOsMitEventMetadatenZurueck() {
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

    Trip trip = new Trip("Duisburg");
    trip.setId(7);
    Event event =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 5, 9, 10, 0),
            LocalDateTime.of(2026, 5, 9, 12, 0),
            25,
            trip);
    event.setId(99);
    event.setCategorie(new Categories("Freizeit"));
    User user = new User("maxmuster", "max@example.com", "hash", "answer");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(ausgabeRepository.findByUserAndDateBetween(
            user, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)))
        .thenReturn(List.of());
    when(eventRepository.findByUsersContainingAndStartTimeBetween(
            user,
            LocalDate.of(2026, 5, 1).atStartOfDay(),
            LocalDate.of(2026, 5, 10).atStartOfDay()))
        .thenReturn(List.of(event));

    List<AusgabeResponseDTO> result =
        ausgabeService.ausgabenliste(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10), "maxmuster");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(99);
    assertThat(result.get(0).getKosten()).isEqualTo(25);
    assertThat(result.get(0).getTrip()).isEqualTo("Duisburg");
    assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 9));
    assertThat(result.get(0).getKategorie()).isEqualTo("Freizeit");
    assertThat(result.get(0).getSourceType()).isEqualTo("EVENT");
    assertThat(result.get(0).getEventName()).isEqualTo("Museum");
    assertThat(result.get(0).getTripId()).isEqualTo(7);
    assertThat(result.get(0).getStart()).isEqualTo(LocalDateTime.of(2026, 5, 9, 10, 0));
    assertThat(result.get(0).getEnd()).isEqualTo(LocalDateTime.of(2026, 5, 9, 12, 0));
  }

  @Test
  void ausgabeProKategorieSummiertKosten() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

    // "speicherst" 2 Ausgaben in derselben Kategorie
    Trip trip1 = new Trip();
    trip1.setName("Paris");
    Ausgabe ausgabe1 =
        new Ausgabe(6767, trip1, LocalDate.of(2026, 5, 11), new Categories("Freizeit"));

    Trip trip2 = new Trip();
    trip2.setName("Barcelona");
    Ausgabe ausgabe2 =
        new Ausgabe(67, trip2, LocalDate.of(2026, 5, 11), new Categories("Freizeit"));

    User user = new User("maxmuster", "max@example.com", "hash", "answer");
    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(ausgabeRepository.findByUserAndDateBetween(
            user, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 11)))
        .thenReturn(List.of(ausgabe1, ausgabe2));
    when(eventRepository.findByUsersContainingAndStartTimeBetween(
            user,
            LocalDate.of(2026, 5, 1).atStartOfDay(),
            LocalDate.of(2026, 5, 11).atStartOfDay()))
        .thenReturn(List.of());

    // fuehrst die Methode aus
    List<KategorieSummeDTO> result =
        ausgabeService.ausgabenProKategorie(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 11), "maxmuster");

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
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

    Trip trip = new Trip("London");
    trip.setId(7);
    Ausgabe y =
        new Ausgabe(100, new Trip("London"), LocalDate.of(2026, 1, 1), new Categories("Freizeit"));
    y.setId(42);
    User user = new User("maxmuster", "max@example.com", "hash", "answer");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(ausgabeRepository.findByUserAndTrip(user, trip)).thenReturn(List.of(y));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId())).thenReturn(List.of());

    // die methode ausfuehren
    List<AusgabeResponseDTO> result = ausgabeService.ausgabenlisteTrip("London", "maxmuster");

    // vergleichen
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getKosten()).isEqualTo(100);
    assertThat(result.get(0).getTrip()).isEqualTo("London");
    assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(result.get(0).getId()).isEqualTo(42);
    assertThat(result.get(0).getSourceType()).isEqualTo("EXPENSE");
    assertThat(result.get(0).getTripId()).isEqualTo(7);
  }

  @Test
  void ausgabenlisteTripGibtEventDTOsMitEventMetadatenZurueck() {
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

    Trip trip = new Trip("London");
    trip.setId(7);
    Event event =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 1, 3, 11, 0),
            LocalDateTime.of(2026, 1, 3, 12, 0),
            25,
            trip);
    event.setId(99);
    event.setCategorie(new Categories("Freizeit"));
    User user = new User("maxmuster", "max@example.com", "hash", "answer");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(ausgabeRepository.findByUserAndTrip(user, trip)).thenReturn(List.of());
    when(eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId()))
        .thenReturn(List.of(event));

    List<AusgabeResponseDTO> result = ausgabeService.ausgabenlisteTrip("London", "maxmuster");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(99);
    assertThat(result.get(0).getKosten()).isEqualTo(25);
    assertThat(result.get(0).getTrip()).isEqualTo("London");
    assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 3));
    assertThat(result.get(0).getKategorie()).isEqualTo("Freizeit");
    assertThat(result.get(0).getSourceType()).isEqualTo("EVENT");
    assertThat(result.get(0).getEventName()).isEqualTo("Museum");
    assertThat(result.get(0).getTripId()).isEqualTo(7);
    assertThat(result.get(0).getStart()).isEqualTo(LocalDateTime.of(2026, 1, 3, 11, 0));
    assertThat(result.get(0).getEnd()).isEqualTo(LocalDateTime.of(2026, 1, 3, 12, 0));
  }

  @Test
  void ausgabeLoeschenLoeschtAusgabeDesAktuellenUsers() {
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

    User user = new User("maxmuster", "max@example.com", "hash", "answer");
    Ausgabe ausgabe =
        new Ausgabe(100, new Trip("London"), LocalDate.of(2026, 1, 1), new Categories("Freizeit"));

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(ausgabeRepository.findByIdAndUser(42L, user)).thenReturn(Optional.of(ausgabe));

    ausgabeService.ausgabeLoeschen(42L, "maxmuster");

    verify(ausgabeRepository).delete(ausgabe);
  }

  @Test
  void ausgabeLoeschenWirftNotFoundWennAusgabeNichtZumUserGehoertOderNichtExistiert() {
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

    User user = new User("maxmuster", "max@example.com", "hash", "answer");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(ausgabeRepository.findByIdAndUser(42L, user)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ausgabeService.ausgabeLoeschen(42L, "maxmuster"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");

    verify(ausgabeRepository, never()).delete(any());
  }

  @Test
  void ausgabenProKategorieTripSummiertAusgabenUndEvents() {
    // du erstellst einen Service
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

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
    User user = new User("maxmuster", "max@example.com", "hash", "answer");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(ausgabeRepository.findByUserAndTrip(user, trip)).thenReturn(List.of(ausgabe1, ausgabe2));
    when(eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId()))
        .thenReturn(List.of(event));

    // fuehrst die Methode aus
    List<KategorieSummeDTO> result = ausgabeService.ausgabenProKategorieTrip("London", "maxmuster");

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
    UserRepository userRepository = mock(UserRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository);

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
    User user = new User("maxmuster", "max@example.com", "hash", "answer");

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(ausgabeRepository.findByUserAndTrip(user, trip)).thenReturn(List.of(ausgabe));
    when(eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId()))
        .thenReturn(List.of(event));

    // fuehrst die Methode aus
    double result = ausgabeService.gesamtausgabenTrip("London", "maxmuster");

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
    UserRepository userRepository = mock(UserRepository.class);
    UserTripRepository userTripRepository = mock(UserTripRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository,
            userTripRepository);

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
    User user = new User("maxmuster", "max@example.com", "hash", "answer");
    UserTrip userTrip = new UserTrip(user, trip, null);
    userTrip.setBudget(300);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(ausgabeRepository.findByUserAndTrip(user, trip)).thenReturn(List.of(ausgabe));
    when(eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId()))
        .thenReturn(List.of(event));
    when(userTripRepository.findByUserAndTrip(user, trip)).thenReturn(Optional.of(userTrip));

    // fuehrst die Methode aus
    double result = ausgabeService.verfuegbarTrip("London", "maxmuster");

    // pruefst ob Budget minus Gesamtausgaben zurueckgegeben wird
    assertThat(result).isEqualTo(175);
  }

  @Test
  void warningRangeGibtWertAusUserTripZurueck() {
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    UserTripRepository userTripRepository = mock(UserTripRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository,
            userTripRepository);

    Trip trip = new Trip("London");
    User user = new User("maxmuster", "max@example.com", "hash", "answer");
    UserTrip userTrip = new UserTrip(user, trip, null);
    userTrip.setWarningRange(50);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(userTripRepository.findByUserAndTrip(user, trip)).thenReturn(Optional.of(userTrip));

    double result = ausgabeService.warningRange("London", "maxmuster");

    assertThat(result).isEqualTo(50);
  }

  @Test
  void warningRangeAktualisierenSpeichertWertInUserTrip() {
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    EventRepository eventRepository = mock(EventRepository.class);
    CategoriesRepository categoriesRepository = mock(CategoriesRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    UserTripRepository userTripRepository = mock(UserTripRepository.class);
    AusgabeService ausgabeService =
        new AusgabeService(
            ausgabeRepository,
            tripRepository,
            eventRepository,
            categoriesRepository,
            userRepository,
            userTripRepository);

    Trip trip = new Trip("London");
    User user = new User("maxmuster", "max@example.com", "hash", "answer");
    UserTrip userTrip = new UserTrip(user, trip, null);

    when(userRepository.findByNameIgnoreCaseOrEmailIgnoreCase("maxmuster", "maxmuster"))
        .thenReturn(Optional.of(user));
    when(tripRepository.findByName("London")).thenReturn(trip);
    when(userTripRepository.findByUserAndTrip(user, trip)).thenReturn(Optional.of(userTrip));

    double result = ausgabeService.warningRangeAktualisieren("London", 75, "maxmuster");

    assertThat(result).isEqualTo(75);
    assertThat(userTrip.getWarningRange()).isEqualTo(75);
    verify(userTripRepository).save(userTrip);
  }
}

package com.sep.sep_backend.ausgaben;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.*;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

public class AusgabenControllerTest {

  @Test
  void controllerHasRestControllerAnnotation() {
    assertThat(AusgabeController.class.getAnnotation(RestController.class)).isNotNull();
  }

  @Test
  void ausgabeHinzufuegenRuftServiceAuf() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    // eine Ausgabe erstellen
    Trip requestedTrip = new Trip("Berlin");
    Trip existingTrip = new Trip("Berlin");
    AusgabeRequestDTO request =
        new AusgabeRequestDTO(10, requestedTrip, LocalDate.of(2026, 5, 11), "Freizeit");

    when(tripRepository.findByName("Berlin")).thenReturn(existingTrip);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");

    // Controller fuehrt die Methode aus
    ausgabeController.ausgabeHinzufuegen(request, authentication);

    // pruefen, ob der Service aufgerufen wurde
    verify(ausgabeService).ausgabeHinzufuegen(request, "maxmuster");
    assertThat(request.getTrip()).isSameAs(existingTrip);
  }

  @Test
  void ausgabelisteGibtServiceErgebnisZurueck() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    LocalDate start = LocalDate.of(2026, 5, 1);
    LocalDate end = LocalDate.of(2026, 5, 11);

    // mache eine Liste von erwarten Ausgaben
    Trip trip = new Trip();
    trip.setName("Oberhausen");
    AusgabeResponseDTO ausgabe =
        new AusgabeResponseDTO(10, trip, LocalDate.of(2026, 5, 11), new Categories());
    List<AusgabeResponseDTO> expected = List.of(ausgabe);

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.ausgabenliste(start, end, "maxmuster")).thenReturn(expected);

    // fuehr die Methode im Controller aus
    List<AusgabeResponseDTO> result = ausgabeController.ausgabenliste(start, end, authentication);

    // pruefe ob er das richtige zurueckgibt und ob der Service aufgerufen wurde
    assertThat(result).isEqualTo(expected);
    verify(ausgabeService).ausgabenliste(start, end, "maxmuster");
  }

  @Test
  void ausgabenProKategorieGibtServiceErgebnisZurueck() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    LocalDate start = LocalDate.of(2026, 5, 1);
    LocalDate end = LocalDate.of(2026, 5, 11);

    // erwartetes Ergebnis
    List<KategorieSummeDTO> expected = new ArrayList<>();
    expected.add(new KategorieSummeDTO("Freizeit", 10));

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.ausgabenProKategorie(start, end, "maxmuster")).thenReturn(expected);

    // tatsaechliches ergebnis
    List<KategorieSummeDTO> result =
        ausgabeController.ausgabenProKategorie(start, end, authentication);

    // beide miteinander vergleichen und pruefen ob der service aufgerufen wurde
    assertThat(result).isEqualTo(expected);
    verify(ausgabeService).ausgabenProKategorie(start, end, "maxmuster");
  }

  @Test
  void ausgabenListeTripGibtServiceErgebnisZurueck() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    Trip trip = new Trip("London");
    List<AusgabeResponseDTO> expected =
        List.of(
            new AusgabeResponseDTO(
                100, trip, LocalDate.of(2026, 1, 1), new Categories("Freizeit")));

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.ausgabenlisteTrip("London", "maxmuster")).thenReturn(expected);

    // tatsaechliches Ergebnis
    List<AusgabeResponseDTO> result = ausgabeController.ausgabenListeTrip("London", authentication);

    // pruefen ob das Service-Ergebnis zurueckgegeben wird
    assertThat(result).isEqualTo(expected);
    verify(ausgabeService).ausgabenlisteTrip("London", "maxmuster");
  }

  @Test
  void ausgabenListeTripWirftNotFoundWennTripNichtExistiert() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.ausgabenlisteTrip("London", "maxmuster")).thenReturn(null);

    // pruefen ob ein Fehler geworfen wird
    assertThatThrownBy(() -> ausgabeController.ausgabenListeTrip("London", authentication))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");
  }

  @Test
  void ausgabenProKategorieTripGibtServiceErgebnisZurueck() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    List<KategorieSummeDTO> expected = List.of(new KategorieSummeDTO("Freizeit", 100));

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.ausgabenProKategorieTrip("London", "maxmuster")).thenReturn(expected);

    // tatsaechliches Ergebnis
    List<KategorieSummeDTO> result =
        ausgabeController.ausgabenProKategorieTrip("London", authentication);

    // pruefen ob das Service-Ergebnis zurueckgegeben wird
    assertThat(result).isEqualTo(expected);
    verify(ausgabeService).ausgabenProKategorieTrip("London", "maxmuster");
  }

  @Test
  void budgetGibtServiceErgebnisZurueck() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.budget("London", "maxmuster")).thenReturn(500.0);

    // tatsaechliches Ergebnis
    double result = ausgabeController.budget("London", authentication);

    // pruefen ob das Budget zurueckgegeben wird
    assertThat(result).isEqualTo(500);
    verify(ausgabeService).budget("London", "maxmuster");
  }

  @Test
  void gesamtausgabenTripGibtServiceErgebnisZurueck() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.gesamtausgabenTrip("London", "maxmuster")).thenReturn(125.0);

    // tatsaechliches Ergebnis
    double result = ausgabeController.gesamtausgabenTrip("London", authentication);

    // pruefen ob das Service-Ergebnis zurueckgegeben wird
    assertThat(result).isEqualTo(125);
    verify(ausgabeService).gesamtausgabenTrip("London", "maxmuster");
  }

  @Test
  void verfuegbarGibtServiceErgebnisZurueck() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.verfuegbarTrip("London", "maxmuster")).thenReturn(175.0);

    // tatsaechliches Ergebnis
    double result = ausgabeController.verfuegbar("London", authentication);

    // pruefen ob das Service-Ergebnis zurueckgegeben wird
    assertThat(result).isEqualTo(175);
    verify(ausgabeService).verfuegbarTrip("London", "maxmuster");
  }

  @Test
  void verfuegbarGibtNullZurueckWennKeinBudgetGesetztIst() {
    // du erstellst dir einen Controller
    AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
    AusgabeService ausgabeService = mock(AusgabeService.class);
    TripRepository tripRepository = mock(TripRepository.class);
    AusgabeController ausgabeController =
        new AusgabeController(ausgabeRepository, ausgabeService, tripRepository);
    Authentication authentication = mock(Authentication.class);

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("maxmuster");
    when(ausgabeService.verfuegbarTrip("London", "maxmuster")).thenReturn(-2.0);

    // tatsaechliches Ergebnis
    double result = ausgabeController.verfuegbar("London", authentication);

    // pruefen ob ohne Budget 0 zurueckgegeben wird
    assertThat(result).isEqualTo(0);
  }

  @Test
  void controllerEndpointsSindKorrektGemappt() throws NoSuchMethodException {
    // prufen ob die Endpunkte von Get und Post Mapping korrekt sind
    RequestMapping controllerMapping = AusgabeController.class.getAnnotation(RequestMapping.class);
    assertThat(controllerMapping.value()).containsExactly("/api");

    Method postMethod =
        AusgabeController.class.getDeclaredMethod(
            "ausgabeHinzufuegen", AusgabeRequestDTO.class, Authentication.class);

    PostMapping postMapping = postMethod.getAnnotation(PostMapping.class);
    assertThat(postMapping.value()).containsExactly("/ausgabe");

    Method listMethod =
        AusgabeController.class.getDeclaredMethod(
            "ausgabenliste", LocalDate.class, LocalDate.class, Authentication.class);

    GetMapping getMapping = listMethod.getAnnotation(GetMapping.class);
    assertThat(getMapping.value()).containsExactly("/ausgabenliste");

    Method tripListMethod =
        AusgabeController.class.getDeclaredMethod(
            "ausgabenListeTrip", String.class, Authentication.class);
    GetMapping tripListMapping = tripListMethod.getAnnotation(GetMapping.class);
    assertThat(tripListMapping.value()).containsExactly("/ausgabenlisteTrip");

    Method proKategorieTripMethod =
        AusgabeController.class.getDeclaredMethod(
            "ausgabenProKategorieTrip", String.class, Authentication.class);
    GetMapping proKategorieTripMapping = proKategorieTripMethod.getAnnotation(GetMapping.class);
    assertThat(proKategorieTripMapping.value()).containsExactly("/proKategorieTrip");

    Method budgetMethod =
        AusgabeController.class.getDeclaredMethod("budget", String.class, Authentication.class);
    GetMapping budgetMapping = budgetMethod.getAnnotation(GetMapping.class);
    assertThat(budgetMapping.value()).containsExactly("/budget");

    Method gesamtausgabenMethod =
        AusgabeController.class.getDeclaredMethod(
            "gesamtausgabenTrip", String.class, Authentication.class);
    GetMapping gesamtausgabenMapping = gesamtausgabenMethod.getAnnotation(GetMapping.class);
    assertThat(gesamtausgabenMapping.value()).containsExactly("/gesamtausgabenTrip");

    Method verfuegbarMethod =
        AusgabeController.class.getDeclaredMethod("verfuegbar", String.class, Authentication.class);
    GetMapping verfuegbarMapping = verfuegbarMethod.getAnnotation(GetMapping.class);
    assertThat(verfuegbarMapping.value()).containsExactly("/verfuegbar");
  }
}

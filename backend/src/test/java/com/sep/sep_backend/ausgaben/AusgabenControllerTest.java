package com.sep.sep_backend.ausgaben;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.*;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class AusgabenControllerTest {

    @Test
    void controllerHasRestControllerAnnotation() {
        assertThat(AusgabeController.class.getAnnotation(RestController.class)).isNotNull();
    }

    @Test
    void ausgabeHinzufuegenRuftServiceAuf(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        // eine Ausgabe erstellen
        Trip requestedTrip = new Trip("Berlin");
        Trip existingTrip = new Trip("Berlin");
        AusgabeRequestDTO request = new AusgabeRequestDTO(10, requestedTrip, LocalDate.of(2026, 5, 11), "Freizeit");

        when(tripRepository.findByName("Berlin")).thenReturn(existingTrip);

        // Controller fuehrt die Methode aus
        ausgabeController.ausgabeHinzufuegen(request);

        // pruefen, ob der Service aufgerufen wurde
        verify(ausgabeService).ausgabeHinzufuegen(request);
        assertThat(request.getTrip()).isSameAs(existingTrip);
    }

    @Test
    void ausgabelisteGibtServiceErgebnisZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 11);

        // mache eine Liste von erwarten Ausgaben
        Trip trip = new Trip();
        trip.setName("Oberhausen");
        AusgabeResponseDTO ausgabe = new AusgabeResponseDTO(10, trip, LocalDate.of(2026, 5, 11), new Categories());
        List<AusgabeResponseDTO> expected = List.of(ausgabe);

        when(ausgabeService.ausgabenliste(start, end)).thenReturn(expected);

        // fuehr die Methode im Controller aus
        List<AusgabeResponseDTO> result = ausgabeController.ausgabenliste(start, end);

        // pruefe ob er das richtige zurueckgibt und ob der Service aufgerufen wurde
        assertThat(result).isEqualTo(expected);
        verify(ausgabeService).ausgabenliste(start, end);
    }

    @Test
    void ausgabenProKategorieGibtServiceErgebnisZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 11);

        // erwartetes Ergebnis
        List<KategorieSummeDTO> expected = new ArrayList<>();
        expected.add(new KategorieSummeDTO("Freizeit", 10));

        when(ausgabeService.ausgabenProKategorie(start, end)).thenReturn(expected);

        // tatsaechliches ergebnis
        List<KategorieSummeDTO> result = ausgabeController.ausgabenProKategorie(start, end);

        // beide miteinander vergleichen und pruefen ob der service aufgerufen wurde
        assertThat(result).isEqualTo(expected);
        verify(ausgabeService).ausgabenProKategorie(start, end);
    }

    @Test
    void ausgabenListeTripGibtServiceErgebnisZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        Trip trip = new Trip("London");
        List<AusgabeResponseDTO> expected = List.of(
                new AusgabeResponseDTO(100, trip, LocalDate.of(2026, 1, 1), new Categories("Freizeit"))
        );

        when(ausgabeService.ausgabenlisteTrip("London")).thenReturn(expected);

        // tatsaechliches Ergebnis
        List<AusgabeResponseDTO> result = ausgabeController.ausgabenListeTrip("London");

        // pruefen ob das Service-Ergebnis zurueckgegeben wird
        assertThat(result).isEqualTo(expected);
        verify(ausgabeService).ausgabenlisteTrip("London");
    }

    @Test
    void ausgabenListeTripWirftNotFoundWennTripNichtExistiert(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        when(ausgabeService.ausgabenlisteTrip("London")).thenReturn(null);

        // pruefen ob ein Fehler geworfen wird
        assertThatThrownBy(() -> ausgabeController.ausgabenListeTrip("London"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void ausgabenProKategorieTripGibtServiceErgebnisZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        List<KategorieSummeDTO> expected = List.of(new KategorieSummeDTO("Freizeit", 100));

        when(ausgabeService.ausgabenProKategorieTrip("London")).thenReturn(expected);

        // tatsaechliches Ergebnis
        List<KategorieSummeDTO> result = ausgabeController.ausgabenProKategorieTrip("London");

        // pruefen ob das Service-Ergebnis zurueckgegeben wird
        assertThat(result).isEqualTo(expected);
        verify(ausgabeService).ausgabenProKategorieTrip("London");
    }

    @Test
    void budgetGibtBudgetVomTripZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        Trip trip = new Trip("London");
        trip.setBudget(500);

        when(tripRepository.findByName("London")).thenReturn(trip);

        // tatsaechliches Ergebnis
        double result = ausgabeController.budget("London");

        // pruefen ob das Budget zurueckgegeben wird
        assertThat(result).isEqualTo(500);
        verify(tripRepository).findByName("London");
    }

    @Test
    void gesamtausgabenTripGibtServiceErgebnisZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        when(ausgabeService.gesamtausgabenTrip("London")).thenReturn(125.0);

        // tatsaechliches Ergebnis
        double result = ausgabeController.gesamtausgabenTrip("London");

        // pruefen ob das Service-Ergebnis zurueckgegeben wird
        assertThat(result).isEqualTo(125);
        verify(ausgabeService).gesamtausgabenTrip("London");
    }

    @Test
    void verfuegbarGibtServiceErgebnisZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        when(ausgabeService.verfuegbarTrip("London")).thenReturn(175.0);

        // tatsaechliches Ergebnis
        double result = ausgabeController.verfuegbar("London");

        // pruefen ob das Service-Ergebnis zurueckgegeben wird
        assertThat(result).isEqualTo(175);
        verify(ausgabeService).verfuegbarTrip("London");
    }

    @Test
    void verfuegbarGibtNullZurueckWennKeinBudgetGesetztIst(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        TripRepository tripRepository = mock(TripRepository.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService, tripRepository);

        when(ausgabeService.verfuegbarTrip("London")).thenReturn(-2.0);

        // tatsaechliches Ergebnis
        double result = ausgabeController.verfuegbar("London");

        // pruefen ob ohne Budget 0 zurueckgegeben wird
        assertThat(result).isEqualTo(0);
    }

    @Test
    void controllerEndpointsSindKorrektGemappt() throws NoSuchMethodException {
        // prufen ob die Endpunkte von Get und Post Mapping korrekt sind
        Method postMethod = AusgabeController.class.getDeclaredMethod(
                "ausgabeHinzufuegen", AusgabeRequestDTO.class);

        PostMapping postMapping = postMethod.getAnnotation(PostMapping.class);
        assertThat(postMapping.value()).containsExactly("/ausgabe", "/api/ausgabe");

        Method listMethod = AusgabeController.class.getDeclaredMethod(
                "ausgabenliste", LocalDate.class, LocalDate.class);

        GetMapping getMapping = listMethod.getAnnotation(GetMapping.class);
        assertThat(getMapping.value()).containsExactly("/ausgabenliste", "/api/ausgabenliste");

        Method tripListMethod = AusgabeController.class.getDeclaredMethod(
                "ausgabenListeTrip", String.class);
        GetMapping tripListMapping = tripListMethod.getAnnotation(GetMapping.class);
        assertThat(tripListMapping.value()).containsExactly("/ausgabenlisteTrip", "/api/ausgabenlisteTrip");

        Method proKategorieTripMethod = AusgabeController.class.getDeclaredMethod(
                "ausgabenProKategorieTrip", String.class);
        GetMapping proKategorieTripMapping = proKategorieTripMethod.getAnnotation(GetMapping.class);
        assertThat(proKategorieTripMapping.value()).containsExactly("/proKategorieTrip", "/api/proKategorieTrip");

        Method budgetMethod = AusgabeController.class.getDeclaredMethod(
                "budget", String.class);
        GetMapping budgetMapping = budgetMethod.getAnnotation(GetMapping.class);
        assertThat(budgetMapping.value()).containsExactly("/budget", "/api/budget");

        Method gesamtausgabenMethod = AusgabeController.class.getDeclaredMethod(
                "gesamtausgabenTrip", String.class);
        GetMapping gesamtausgabenMapping = gesamtausgabenMethod.getAnnotation(GetMapping.class);
        assertThat(gesamtausgabenMapping.value()).containsExactly("/gesamtausgabenTrip", "/api/gesamtausgabenTrip");

        Method verfuegbarMethod = AusgabeController.class.getDeclaredMethod(
                "verfuegbar", String.class);
        GetMapping verfuegbarMapping = verfuegbarMethod.getAnnotation(GetMapping.class);
        assertThat(verfuegbarMapping.value()).containsExactly("/verfuegbar", "/api/verfuegbar");
    }
}

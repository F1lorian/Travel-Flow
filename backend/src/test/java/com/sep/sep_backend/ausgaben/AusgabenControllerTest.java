package com.sep.sep_backend.ausgaben;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.*;
import com.sep.sep_backend.repos.AusgabeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService);

        // eine Ausgabe erstellen
        AusgabeRequestDTO request = new AusgabeRequestDTO();

        // Controller fuehrt die Methode aus
        ausgabeController.ausgabeHinzufuegen(request);

        // pruefen, ob der Service aufgerufen wurde
        verify(ausgabeService).ausgabeHinzufuegen(request);
    }

    @Test
    void ausgabelisteGibtServiceErgebnisZurueck(){
        // du erstellst dir einen Controller
        AusgabeRepository ausgabeRepository = mock(AusgabeRepository.class);
        AusgabeService ausgabeService = mock(AusgabeService.class);
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 11);

        // mache eine Liste von erwarten Ausgaben
        Trip trip = new Trip();
        trip.setName("Oberhausen");
        AusgabeResponseDTO ausgabe = new AusgabeResponseDTO(10, trip, LocalDate.of(2026, 5, 11), Kategorie.FREIZEIT);
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
        AusgabeController ausgabeController = new  AusgabeController(ausgabeRepository, ausgabeService);

        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 11);

        // erwartetes Ergebnis
        Map<Kategorie, Double> expected = new HashMap<>();
        expected.put(Kategorie.SONSTIGES, 15.0);

        when(ausgabeService.ausgabenProKategorie(start, end)).thenReturn(expected);

        // tatsaechliches ergebnis
        Map<Kategorie, Double> result = ausgabeController.ausgabenProKategorie(start, end);

        // beide miteinander vergleichen und pruefen ob der service aufgerufen wurde
        assertThat(result).isEqualTo(expected);
        verify(ausgabeService).ausgabenProKategorie(start, end);
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
    }
}

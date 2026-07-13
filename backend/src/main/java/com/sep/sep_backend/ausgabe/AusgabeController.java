package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for all expense-related operations.
 *
 * <p>This controller is used by the Angular frontend for: - adding expenses - loading expenses by
 * date interval - loading expenses by trip - loading category summaries for charts -
 * reading/updating trip budgets - reading/updating custom warning ranges - calculating available
 * budget
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class AusgabeController {

  private final AusgabeRepository ausgabeRepo;
  private AusgabeService ausgabeService;
  private TripRepository tripRepository;

  /**
   * Creates a controller for expense endpoints.
   *
   * @param ausgabeRepo repository for direct expense access
   * @param ausgabeService service containing expense business logic
   * @param tripRepository repository used to resolve trip names from incoming requests
   */
  @Autowired
  public AusgabeController(
      AusgabeRepository ausgabeRepo, AusgabeService ausgabeService, TripRepository tripRepository) {
    this.ausgabeRepo = ausgabeRepo;
    this.ausgabeService = ausgabeService;
    this.tripRepository = tripRepository;
  }

  /**
   * Adds a new expense to an existing trip.
   *
   * <p>The frontend sends the trip as an object containing the trip name. Before saving the
   * expense, the controller verifies that the trip exists.
   *
   * <p>Returns: - 400 if no trip name was provided - 404 if the trip does not exist
   */
  @PostMapping("/ausgabe")
  public void ausgabeHinzufuegen(
      @RequestBody AusgabeRequestDTO dto, Authentication authentication) {
    if (dto.getTrip() == null
        || dto.getTrip().getName() == null
        || dto.getTrip().getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip name is required");
    }

    Trip trip = tripRepository.findByName(dto.getTrip().getName());

    if (trip == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    // Replace the lightweight trip object from the request with the persisted entity.
    dto.setTrip(trip);

    ausgabeService.ausgabeHinzufuegen(dto, getCurrentUsername(authentication));
  }

  /** Deletes a manually created expense owned by the current user. */
  @DeleteMapping("/ausgabe/{id}")
  public void ausgabeLoeschen(@PathVariable Long id, Authentication authentication) {
    ausgabeService.ausgabeLoeschen(id, getCurrentUsername(authentication));
  }

  /**
   * Extracts the current username from the Spring Security authentication.
   *
   * @param authentication current request authentication
   * @return authenticated username
   * @throws ResponseStatusException if no authenticated user is available
   */
  private String getCurrentUsername(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
    }

    return authentication.getName();
  }

  /**
   * Returns all expenses within a selected date interval. Used by the general spending overview.
   */
  @GetMapping("/ausgabenliste")
  public List<AusgabeResponseDTO> ausgabenliste(
      @RequestParam LocalDate start, @RequestParam LocalDate end, Authentication authentication) {
    return ausgabeService.ausgabenliste(start, end, getCurrentUsername(authentication));
  }

  /**
   * Returns the summarized expenses per category for a selected date interval. Used for the
   * category table and chart in the general spending overview.
   */
  @GetMapping("/proKategorie")
  public List<KategorieSummeDTO> ausgabenProKategorie(
      @RequestParam LocalDate start, @RequestParam LocalDate end, Authentication authentication) {
    return ausgabeService.ausgabenProKategorie(start, end, getCurrentUsername(authentication));
  }

  /** Returns all expenses for one specific trip. Used by the trip-specific spending overview. */
  @GetMapping("/ausgabenlisteTrip")
  public List<AusgabeResponseDTO> ausgabenListeTrip(
      @RequestParam String tripname, Authentication authentication) {
    List<AusgabeResponseDTO> result =
        ausgabeService.ausgabenlisteTrip(tripname, getCurrentUsername(authentication));

    if (result == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }

  /**
   * Returns summarized expenses per category for one specific trip. Used for the pie chart and
   * category table on the trip spending overview page.
   */
  @GetMapping("/proKategorieTrip")
  public List<KategorieSummeDTO> ausgabenProKategorieTrip(
      @RequestParam String tripname, Authentication authentication) {
    List<KategorieSummeDTO> result =
        ausgabeService.ausgabenProKategorieTrip(tripname, getCurrentUsername(authentication));

    if (result == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }

  /** Returns the custom budget limit of a trip. */
  @GetMapping("/budget")
  public double budget(@RequestParam String tripname, Authentication authentication) {
    double result = ausgabeService.budget(tripname, getCurrentUsername(authentication));

    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }

  /**
   * Updates the custom budget limit of a trip.
   *
   * <p>The budget must not be negative. Returns the updated budget value.
   */
  @PutMapping("/budget")
  public double budgetAktualisieren(
      @RequestParam String tripname, @RequestParam double budget, Authentication authentication) {
    if (budget < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget must not be negative");
    }

    double result =
        ausgabeService.budgetAktualisieren(tripname, budget, getCurrentUsername(authentication));

    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }

  /**
   * Returns the custom warning range of a trip.
   *
   * <p>The warning range is interpreted as a remaining-budget threshold. Example: warningRange =
   * 100 means the user is warned if 100 € or less remain.
   */
  @GetMapping("/warningRange")
  public double warningRange(@RequestParam String tripname, Authentication authentication) {
    double result = ausgabeService.warningRange(tripname, getCurrentUsername(authentication));

    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }

  /**
   * Updates the custom warning range of a trip.
   *
   * <p>The warning range must not be negative. Returns the updated warning range.
   */
  @PutMapping("/warningRange")
  public double warningRangeAktualisieren(
      @RequestParam String tripname,
      @RequestParam double warningRange,
      Authentication authentication) {
    if (warningRange < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Warning range must not be negative");
    }

    double result =
        ausgabeService.warningRangeAktualisieren(
            tripname, warningRange, getCurrentUsername(authentication));

    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }

  /**
   * Returns the sum of all expenses for a specific trip.
   *
   * <p>The service returns a negative value if the trip was not found.
   */
  @GetMapping("/gesamtausgabenTrip")
  public double gesamtausgabenTrip(@RequestParam String tripname, Authentication authentication) {
    double result = ausgabeService.gesamtausgabenTrip(tripname, getCurrentUsername(authentication));

    if (result < 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }

  /**
   * Returns the remaining available budget for a trip.
   *
   * <p>Service return values: - -1 means the trip was not found - -2 means no available budget
   * could be calculated; the frontend receives 0 instead
   */
  @GetMapping("/verfuegbar")
  public double verfuegbar(@RequestParam String tripname, Authentication authentication) {
    double result = ausgabeService.verfuegbarTrip(tripname, getCurrentUsername(authentication));

    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    if (result == -2) {
      return 0;
    }

    return result;
  }

  /**
   * Downloads the current user's expenses and event costs for a date interval as a CSV file.
   *
   * @param start first date of the interval
   * @param end last date of the interval
   * @param authentication current request authentication
   * @return response containing CSV content and download headers
   */
  @GetMapping(value = "/ausgabenliste/export", produces = "text/csv")
  public ResponseEntity<String> exportAusgabenCsv(
      @RequestParam LocalDate start, @RequestParam LocalDate end, Authentication authentication) {
    String csv = ausgabeService.exportAusgabenCsv(start, end, getCurrentUsername(authentication));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cashbook-export.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv);
  }

  /**
   * Downloads the current user's expenses and event costs for one trip as a CSV file.
   *
   * @param tripname name of the trip
   * @param authentication current request authentication
   * @return response containing CSV content and download headers
   * @throws ResponseStatusException if the trip does not exist
   */
  @GetMapping(value = "/ausgabenlisteTrip/export", produces = "text/csv")
  public ResponseEntity<String> exportAusgabenTripCsv(
      @RequestParam String tripname, Authentication authentication) {
    String csv = ausgabeService.exportAusgabenTripCsv(tripname, getCurrentUsername(authentication));

    if (csv == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cashbook-export.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv);
  }

  /** Returns an expense forecast for one trip for the next seven days. */
  @GetMapping(value = "/forecast")
  public ForecastDTO forecast(
      @RequestParam LocalDate today,
      @RequestParam String tripname,
      Authentication authentication) {
    ForecastDTO result =
        ausgabeService.forecast(today, tripname, getCurrentUsername(authentication));

    if (result == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }

    return result;
  }
}

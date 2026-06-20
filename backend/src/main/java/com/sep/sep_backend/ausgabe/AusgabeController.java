package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  @Autowired
  public AusgabeController(
      AusgabeRepository ausgabeRepo, AusgabeService ausgabeService, TripRepository tripRepository) {
    this.ausgabeRepo = ausgabeRepo;
    this.ausgabeService = ausgabeService;
    this.tripRepository = tripRepository;
  }

  private Trip requireOwnedTrip(String tripname, String username) {
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }
    if (tripRepository.countByUsernameAndTripId(username, trip.getId()) == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }
    return trip;
  }

  /**
   * Adds a new expense to an existing trip.
   *
   * <p>The frontend sends the trip as an object containing the trip name. Before saving the
   * expense, the controller verifies that the trip exists and belongs to the current user.
   *
   * <p>Returns: - 400 if no trip name was provided - 404 if the trip does not exist
   */
  @PostMapping("/ausgabe")
  public void ausgabeHinzufuegen(
      @RequestBody AusgabeRequestDTO dto, @AuthenticationPrincipal String username) {
    if (dto.getTrip() == null
        || dto.getTrip().getName() == null
        || dto.getTrip().getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip name is required");
    }

    Trip trip = requireOwnedTrip(dto.getTrip().getName(), username);
    dto.setTrip(trip);
    ausgabeService.ausgabeHinzufuegen(dto);
  }

  /**
   * Returns all expenses within a selected date interval. Used by the general spending overview.
   */
  @GetMapping("/ausgabenliste")
  public List<AusgabeResponseDTO> ausgabenliste(
      @RequestParam LocalDate start, @RequestParam LocalDate end) {
    return ausgabeService.ausgabenliste(start, end);
  }

  /**
   * Returns the summarized expenses per category for a selected date interval. Used for the
   * category table and chart in the general spending overview.
   */
  @GetMapping("/proKategorie")
  public List<KategorieSummeDTO> ausgabenProKategorie(
      @RequestParam LocalDate start, @RequestParam LocalDate end) {
    return ausgabeService.ausgabenProKategorie(start, end);
  }

  /** Returns all expenses for one specific trip. Used by the trip-specific spending overview. */
  @GetMapping("/ausgabenlisteTrip")
  public List<AusgabeResponseDTO> ausgabenListeTrip(
      @RequestParam String tripname, @AuthenticationPrincipal String username) {
    requireOwnedTrip(tripname, username);
    List<AusgabeResponseDTO> result = ausgabeService.ausgabenlisteTrip(tripname);
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
      @RequestParam String tripname, @AuthenticationPrincipal String username) {
    requireOwnedTrip(tripname, username);
    List<KategorieSummeDTO> result = ausgabeService.ausgabenProKategorieTrip(tripname);
    if (result == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }
    return result;
  }

  /** Returns the custom budget limit of a trip. */
  @GetMapping("/budget")
  public double budget(@RequestParam String tripname, @AuthenticationPrincipal String username) {
    Trip trip = requireOwnedTrip(tripname, username);
    return trip.getBudget();
  }

  /** Updates the custom budget limit of a trip. Returns the updated budget value. */
  @PutMapping("/budget")
  public double budgetAktualisieren(
      @RequestParam String tripname,
      @RequestParam double budget,
      @AuthenticationPrincipal String username) {
    if (budget < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget must not be negative");
    }
    Trip trip = requireOwnedTrip(tripname, username);
    trip.setBudget(budget);
    tripRepository.save(trip);
    return trip.getBudget();
  }

  /**
   * Returns the custom warning range of a trip.
   *
   * <p>The warning range is interpreted as a remaining-budget threshold. Example: warningRange =
   * 100 means the user is warned if 100 € or less remain.
   */
  @GetMapping("/warningRange")
  public double warningRange(
      @RequestParam String tripname, @AuthenticationPrincipal String username) {
    Trip trip = requireOwnedTrip(tripname, username);
    return trip.getWarningRange();
  }

  /** Updates the custom warning range of a trip. Returns the updated warning range. */
  @PutMapping("/warningRange")
  public double warningRangeAktualisieren(
      @RequestParam String tripname,
      @RequestParam double warningRange,
      @AuthenticationPrincipal String username) {
    if (warningRange < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Warning range must not be negative");
    }
    Trip trip = requireOwnedTrip(tripname, username);
    trip.setWarningRange(warningRange);
    tripRepository.save(trip);
    return trip.getWarningRange();
  }

  /** Returns the sum of all expenses for a specific trip. */
  @GetMapping("/gesamtausgabenTrip")
  public double gesamtausgabenTrip(
      @RequestParam String tripname, @AuthenticationPrincipal String username) {
    requireOwnedTrip(tripname, username);
    double result = ausgabeService.gesamtausgabenTrip(tripname);
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
  public double verfuegbar(
      @RequestParam String tripname, @AuthenticationPrincipal String username) {
    requireOwnedTrip(tripname, username);
    double result = ausgabeService.verfuegbarTrip(tripname);
    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
    }
    if (result == -2) {
      return 0;
    }
    return result;
  }
}

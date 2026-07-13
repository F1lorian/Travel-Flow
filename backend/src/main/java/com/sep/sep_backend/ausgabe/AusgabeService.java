package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.entities.*;
import com.sep.sep_backend.repos.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AusgabeService {

  AusgabeRepository repo;
  TripRepository tripRepository;
  EventRepository eventRepository;
  CategoriesRepository categoriesRepository;
  UserRepository userRepository;
  UserTripRepository userTripRepository;

  /**
   * Creates an expense service with all repositories needed for user-specific spending operations.
   */
  @Autowired
  public AusgabeService(
      AusgabeRepository repo,
      TripRepository tripRepository,
      EventRepository eventRepository,
      CategoriesRepository categoriesRepository,
      UserRepository userRepository,
      UserTripRepository userTripRepository) {
    this.repo = repo;
    this.tripRepository = tripRepository;
    this.eventRepository = eventRepository;
    this.categoriesRepository = categoriesRepository;
    this.userRepository = userRepository;
    this.userTripRepository = userTripRepository;
  }

  /** Creates an expense service for tests that do not need user-trip memberships. */
  public AusgabeService(
      AusgabeRepository repo,
      TripRepository tripRepository,
      EventRepository eventRepository,
      CategoriesRepository categoriesRepository,
      UserRepository userRepository) {
    this(repo, tripRepository, eventRepository, categoriesRepository, userRepository, null);
  }

  /** Creates an expense service for tests that do not need authenticated users. */
  public AusgabeService(
      AusgabeRepository repo,
      TripRepository tripRepository,
      EventRepository eventRepository,
      CategoriesRepository categoriesRepository) {
    this(repo, tripRepository, eventRepository, categoriesRepository, null, null);
  }

  /**
   * Adds a new expense for the current user to one of the user's trips.
   *
   * @param dto request data containing cost, trip, date, and category
   * @param username authenticated user's name or email
   * @throws ResponseStatusException if the category does not exist or the user is not part of the
   *     selected trip
   */
  public void ausgabeHinzufuegen(AusgabeRequestDTO dto, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Categories category =
        categoriesRepository
            .findByName(dto.getKategorie())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));

    Trip trip = dto.getTrip();
    if (userTripRepository.findByUserAndTrip(user, trip).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip not found");
    }
    Ausgabe ausgabe = new Ausgabe(dto.getKosten(), trip, dto.getDate(), category);
    ausgabe.setUser(user);
    repo.save(ausgabe);
  }

  /**
   * Returns the current user's expenses and event costs within the selected date interval.
   *
   * @param start first date of the interval
   * @param end last date of the interval
   * @param username authenticated user's name or email
   * @return combined list of expenses and event costs
   */
  public List<AusgabeResponseDTO> ausgabenliste(LocalDate start, LocalDate end, String username) {
    User user = this.getCurrentUserOrThrow(username);
    List<Ausgabe> ausgaben = repo.findByUserAndDateBetween(user, start, end);
    List<Event> events =
        eventRepository.findByUsersContainingAndStartTimeBetween(
            user, start.atStartOfDay(), end.atStartOfDay());
    List<AusgabeResponseDTO> ausgabenDTOs = new ArrayList<>();
    for (int i = 0; i < ausgaben.size(); i++) {
      Ausgabe x = ausgaben.get(i);
      AusgabeResponseDTO y =
          new AusgabeResponseDTO(
              x.getId(), x.getKosten(), x.getTrip(), x.getDate(), x.getKategorie());
      ausgabenDTOs.add(y);
    }
    for (int i = 0; i < events.size(); i++) {
      Event x = events.get(i);
      ausgabenDTOs.add(new AusgabeResponseDTO(x));
    }
    return ausgabenDTOs;
  }

  /**
   * Sums the current user's expenses and event costs by category for a date interval.
   *
   * @param start first date of the interval
   * @param end last date of the interval
   * @param username authenticated user's name or email
   * @return category totals for the selected interval
   */
  public List<KategorieSummeDTO> ausgabenProKategorie(
      LocalDate start, LocalDate end, String username) {
    User user = this.getCurrentUserOrThrow(username);
    List<Ausgabe> ausgaben = repo.findByUserAndDateBetween(user, start, end);
    List<Event> events =
        eventRepository.findByUsersContainingAndStartTimeBetween(
            user, start.atStartOfDay(), end.atStartOfDay());

    Map<String, Double> summen = new HashMap<>();

    for (Ausgabe ausgabe : ausgaben) {
      String kategorie =
          ausgabe.getKategorie() != null ? ausgabe.getKategorie().getName() : "OTHER";

      double kosten = ausgabe.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    for (Event event : events) {
      String kategorie = event.getCategorie() != null ? event.getCategorie().getName() : "OTHER";

      double kosten = event.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    List<KategorieSummeDTO> result = new ArrayList<>();
    for (Map.Entry<String, Double> entry : summen.entrySet()) {
      result.add(new KategorieSummeDTO(entry.getKey(), entry.getValue()));
    }

    return result;
  }

  /**
   * Returns the current user's expenses and event costs for a specific trip.
   *
   * @param tripname name of the trip
   * @param username authenticated user's name or email
   * @return combined list of trip expenses and event costs, or {@code null} if the trip does not
   *     exist
   */
  public List<AusgabeResponseDTO> ausgabenlisteTrip(String tripname, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return null;
    }
    List<Ausgabe> ausgaben = repo.findByUserAndTrip(user, trip);
    List<Event> events = eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId());
    List<AusgabeResponseDTO> ausgabenDTOs = new ArrayList<>();
    for (Ausgabe ausgabe : ausgaben) {
      AusgabeResponseDTO dto =
          new AusgabeResponseDTO(
              ausgabe.getId(),
              ausgabe.getKosten(),
              trip,
              ausgabe.getDate(),
              ausgabe.getKategorie());
      ausgabenDTOs.add(dto);
    }
    for (Event event : events) {
      ausgabenDTOs.add(new AusgabeResponseDTO(event));
    }
    return ausgabenDTOs;
  }

  /**
   * Deletes a manually created expense owned by the current user.
   *
   * @param id expense identifier
   * @param username authenticated user's name or email
   * @throws ResponseStatusException if the expense does not exist for the current user
   */
  public void ausgabeLoeschen(Long id, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Ausgabe ausgabe =
        repo.findByIdAndUser(id, user)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

    repo.delete(ausgabe);
  }

  /**
   * Sums the current user's expenses and event costs by category for a specific trip.
   *
   * @param tripname name of the trip
   * @param username authenticated user's name or email
   * @return category totals for the trip, or {@code null} if the trip does not exist
   */
  public List<KategorieSummeDTO> ausgabenProKategorieTrip(String tripname, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return null;
    }

    List<Ausgabe> ausgaben = repo.findByUserAndTrip(user, trip);
    List<Event> events = eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId());

    Map<String, Double> summen = new HashMap<>();

    for (Ausgabe ausgabe : ausgaben) {
      String kategorie =
          ausgabe.getKategorie() != null ? ausgabe.getKategorie().getName() : "OTHER";

      double kosten = ausgabe.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    for (Event event : events) {
      String kategorie = event.getCategorie() != null ? event.getCategorie().getName() : "OTHER";

      double kosten = event.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    List<KategorieSummeDTO> result = new ArrayList<>();
    for (Map.Entry<String, Double> entry : summen.entrySet()) {
      result.add(new KategorieSummeDTO(entry.getKey(), entry.getValue()));
    }

    return result;
  }

  /**
   * Calculates the current user's total expenses and event costs for a trip.
   *
   * @param tripname name of the trip
   * @param username authenticated user's name or email
   * @return total spending for the trip, or {@code -1} if the trip does not exist
   */
  public double gesamtausgabenTrip(String tripname, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }
    double summe = 0;
    List<Ausgabe> ausgaben = repo.findByUserAndTrip(user, trip);
    List<Event> events = eventRepository.findByUsersContainingAndTrip_Id(user, trip.getId());
    for (Ausgabe ausgabe : ausgaben) {
      summe = summe + ausgabe.getKosten();
    }
    for (Event event : events) {
      summe = summe + event.getKosten();
    }
    return summe;
  }

  /**
   * Calculates the current user's remaining budget for a trip.
   *
   * @param tripname name of the trip
   * @param username authenticated user's name or email
   * @return budget minus total spending, {@code -1} if the trip does not exist, or {@code -2} if no
   *     budget is set
   */
  public double verfuegbarTrip(String tripname, String username) {
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }
    double ausgaben = gesamtausgabenTrip(trip.getName(), username);
    double budget = budget(trip.getName(), username);

    if (budget == 0) {
      return -2;
    }

    return budget - ausgaben;
  }

  /**
   * Returns the current user's budget for a trip.
   *
   * @param tripname name of the trip
   * @param username authenticated user's name or email
   * @return the configured budget, {@code 0} if none is set, or {@code -1} if the trip does not
   *     exist
   */
  public double budget(String tripname, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }

    return userTripRepository.findByUserAndTrip(user, trip).map(UserTrip::getBudget).orElse(0.0);
  }

  /**
   * Updates the current user's budget for a trip.
   *
   * @param tripname name of the trip
   * @param budget new budget value
   * @param username authenticated user's name or email
   * @return updated budget, or {@code -1} if the trip does not exist
   * @throws ResponseStatusException if the user is not part of the selected trip
   */
  public double budgetAktualisieren(String tripname, double budget, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }

    UserTrip userTrip =
        userTripRepository
            .findByUserAndTrip(user, trip)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    userTrip.setBudget(budget);
    userTripRepository.save(userTrip);

    return userTrip.getBudget();
  }

  /**
   * Returns the current user's warning range for a trip.
   *
   * @param tripname name of the trip
   * @param username authenticated user's name or email
   * @return the configured warning range, {@code 0} if none is set, or {@code -1} if the trip does
   *     not exist
   */
  public double warningRange(String tripname, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }

    return userTripRepository
        .findByUserAndTrip(user, trip)
        .map(UserTrip::getWarningRange)
        .orElse(0.0);
  }

  /**
   * Updates the current user's warning range for a trip.
   *
   * @param tripname name of the trip
   * @param warningRange new remaining-budget warning threshold
   * @param username authenticated user's name or email
   * @return updated warning range, or {@code -1} if the trip does not exist
   * @throws ResponseStatusException if the user is not part of the selected trip
   */
  public double warningRangeAktualisieren(String tripname, double warningRange, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }

    UserTrip userTrip =
        userTripRepository
            .findByUserAndTrip(user, trip)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    userTrip.setWarningRange(warningRange);
    userTripRepository.save(userTrip);

    return userTrip.getWarningRange();
  }

  /**
   * Loads the authenticated user by username or email.
   *
   * @param username authenticated user's name or email
   * @return matching user entity
   * @throws ResponseStatusException if no user can be found
   */
  private User getCurrentUserOrThrow(String username) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, username)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
  }

  /**
   * Exports the current user's expenses and event costs within a date interval as CSV.
   *
   * @param start first date of the interval
   * @param end last date of the interval
   * @param username authenticated user's name or email
   * @return CSV content containing the matching expenses and events
   */
  public String exportAusgabenCsv(LocalDate start, LocalDate end, String username) {
    List<AusgabeResponseDTO> rows = ausgabenliste(start, end, username);
    return buildCsv(rows);
  }

  /**
   * Exports the current user's expenses and event costs for one trip as CSV.
   *
   * @param tripname name of the trip
   * @param username authenticated user's name or email
   * @return CSV content for the trip, or {@code null} if the trip does not exist
   */
  public String exportAusgabenTripCsv(String tripname, String username) {
    List<AusgabeResponseDTO> rows = ausgabenlisteTrip(tripname, username);
    if (rows == null) {
      return null;
    }
    return buildCsv(rows);
  }

  /**
   * Builds CSV content from expense response rows.
   *
   * @param rows expenses and events that should be written to the CSV file
   * @return CSV content with a header row and one line per expense or event
   */
  public String buildCsv(List<AusgabeResponseDTO> rows) {
    StringBuilder csv = new StringBuilder();
    csv.append("Date,Title,Trip,Category,Amount\n");
    for (AusgabeResponseDTO row : rows) {
      csv.append(escapeCsv(row.getDate()))
          .append(",")
          .append(escapeCsv(row.getEventName() != null ? row.getEventName() : "Expense"))
          .append(",")
          .append(escapeCsv(row.getTrip()))
          .append(",")
          .append(escapeCsv(row.getKategorie()))
          .append(",")
          .append(row.getKosten())
          .append("\n");
    }
    return csv.toString();
  }

  /**
   * Escapes a value so it can be safely written as one CSV field.
   *
   * @param value field value to escape
   * @return escaped CSV field, or an empty string for {@code null}
   */
  public String escapeCsv(Object value) {
    if (value == null) {
      return "";
    }

    String text = value.toString();
    if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
      return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    return text;
  }

  /**
   * Predicts the authenticated user's spending for one trip for the next seven days using linear
   * regression.
   *
   * <p>The prediction is based on the user's daily expenses and event costs from the beginning of
   * the selected trip through yesterday. Days without expenses are included with a value of zero.
   * Negative predicted values are treated as zero.
   *
   * @param today reference date used to determine the historical and forecast periods
   * @param tripname name of the trip to forecast
   * @param username authenticated user's name or email
   * @return forecast containing the average predicted daily spending and the predicted total for
   *     the next seven days, or {@code null} if the trip does not exist
   */
  public ForecastDTO forecast(LocalDate today, String tripname, String username) {
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return null;
    }
    if (trip.getStartDate() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Trip start date is required for a forecast");
    }

    // Der heutige Tag wird nicht verwendet, weil noch Ausgaben hinzukommen koennen.
    LocalDate start = trip.getStartDate();
    LocalDate end = today.minusDays(1);

    // Es werden nur Ausgaben und Eventkosten der ausgewaehlten Reise beruecksichtigt.
    List<AusgabeResponseDTO> expenses = ausgabenlisteTrip(tripname, username);
    if (expenses == null) {
      return null;
    }

    int completedDays = (int) java.time.temporal.ChronoUnit.DAYS.between(start, today);
    if (completedDays <= 0) {
      return new ForecastDTO(0, 0);
    }

    // Jeder Eintrag steht fuer einen abgeschlossenen Reisetag.
    // Tage ohne Ausgaben bleiben bei 0 und werden ebenfalls in der linearen Regression beruecksichtigt
    double[] dailyExpenses = new double[completedDays];

    // Jede Ausgabe wird dem passenden abgeschlossenen Reisetag zugeordnet.
    for (AusgabeResponseDTO expense : expenses) {
      if (expense.getDate().isBefore(start) || expense.getDate().isAfter(end)) {
        continue;
      }

      int dayIndex = (int) java.time.temporal.ChronoUnit.DAYS.between(
              start, expense.getDate());

      // Diese Pruefung verhindert einen Zugriff ausserhalb des Arrays.
      if (dayIndex >= 0 && dayIndex < dailyExpenses.length) {
        dailyExpenses[dayIndex] += expense.getKosten();
      }
    }

    // Bei der Regression ist x der Tagesindex seit Reisebeginn und y sind die
    // Ausgaben des jeweiligen Tages. Zuerst werden beide Mittelwerte berechnet.
    double meanX = 0;
    double meanY = 0;

    for (int i = 0; i < dailyExpenses.length; i++) {
      meanX += i;
      meanY += dailyExpenses[i];
    }

    meanX /= dailyExpenses.length;
    meanY /= dailyExpenses.length;

    // Zaehler und Nenner werden zur Berechnung der Steigung benoetigt:
    // Steigung = Summe((x - meanX) * (y - meanY)) / Summe((x - meanX)^2)
    double numerator = 0;
    double denominator = 0;

    for (int i = 0; i < dailyExpenses.length; i++) {
      double xDifference = i - meanX;
      double yDifference = dailyExpenses[i] - meanY;

      numerator += xDifference * yDifference;
      denominator += xDifference * xDifference;
    }

    // Bei nur einem abgeschlossenen Reisetag wird dessen Wert als konstante Prognose verwendet.
    double slope = denominator == 0 ? 0 : numerator / denominator;
    double intercept = meanY - slope * meanX;

    double weeklyForecast = 0;

    // Die sieben Indizes nach den abgeschlossenen Reisetagen bilden den Prognosezeitraum.
    for (int dayIndex = completedDays; dayIndex < completedDays + 7; dayIndex++) {
      double prediction = slope * dayIndex + intercept;

      // Vorhergesagte Ausgaben koennen nicht negativ sein.
      weeklyForecast += Math.max(0, prediction);
    }

    // Der Tageswert ist der Durchschnitt der sieben vorhergesagten Tage
    double dailyForecast = weeklyForecast / 7;

    // Die Geldbetraege werden auf zwei Nachkommastellen gerundet.
    dailyForecast = Math.round(dailyForecast * 100.0) / 100.0;
    weeklyForecast = Math.round(weeklyForecast * 100.0) / 100.0;

    return new ForecastDTO(dailyForecast, weeklyForecast);
  }

}

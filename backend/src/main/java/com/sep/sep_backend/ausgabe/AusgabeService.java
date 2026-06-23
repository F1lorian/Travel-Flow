package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.*;
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

  public AusgabeService(
      AusgabeRepository repo,
      TripRepository tripRepository,
      EventRepository eventRepository,
      CategoriesRepository categoriesRepository,
      UserRepository userRepository) {
    this(repo, tripRepository, eventRepository, categoriesRepository, userRepository, null);
  }

  public AusgabeService(
      AusgabeRepository repo,
      TripRepository tripRepository,
      EventRepository eventRepository,
      CategoriesRepository categoriesRepository) {
    this(repo, tripRepository, eventRepository, categoriesRepository, null, null);
  }

  // Ausgabe hinzufuegen
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

  // die Methode gibt eine Liste von Ausgaben in dem gewählten Interval
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
          new AusgabeResponseDTO(x.getKosten(), x.getTrip(), x.getDate(), x.getKategorie());
      ausgabenDTOs.add(y);
    }
    for (int i = 0; i < events.size(); i++) {
      Event x = events.get(i);
      AusgabeResponseDTO y =
          new AusgabeResponseDTO(
              x.getKosten(), x.getTrip(), x.getStartTime().toLocalDate(), x.getCategorie());
      ausgabenDTOs.add(y);
    }
    return ausgabenDTOs;
  }

  // eine Darstellung von Kategorie und ihre Gesamtkosten in dem gewählten Interval
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
          ausgabe.getKategorie() != null ? ausgabe.getKategorie().getName() : "SONSTIGES";

      double kosten = ausgabe.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    for (Event event : events) {
      String kategorie =
          event.getCategorie() != null ? event.getCategorie().getName() : "SONSTIGES";

      double kosten = event.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    List<KategorieSummeDTO> result = new ArrayList<>();
    for (Map.Entry<String, Double> entry : summen.entrySet()) {
      result.add(new KategorieSummeDTO(entry.getKey(), entry.getValue()));
    }

    return result;
  }

  // Methode liefert Liste von Ausgaben von diesem Trip
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
              ausgabe.getKosten(), trip, ausgabe.getDate(), ausgabe.getKategorie());
      ausgabenDTOs.add(dto);
    }
    for (Event event : events) {
      AusgabeResponseDTO dto =
          new AusgabeResponseDTO(
              event.getKosten(), trip, event.getStartTime().toLocalDate(), event.getCategorie());
      ausgabenDTOs.add(dto);
    }
    return ausgabenDTOs;
  }

  // eine Darstellung von Kategorie und ihre Gesamtkosten fuer ein bestimmten Trip
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
          ausgabe.getKategorie() != null ? ausgabe.getKategorie().getName() : "SONSTIGES";

      double kosten = ausgabe.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    for (Event event : events) {
      String kategorie =
          event.getCategorie() != null ? event.getCategorie().getName() : "SONSTIGES";

      double kosten = event.getKosten();
      summen.put(kategorie, summen.getOrDefault(kategorie, 0.0) + kosten);
    }

    List<KategorieSummeDTO> result = new ArrayList<>();
    for (Map.Entry<String, Double> entry : summen.entrySet()) {
      result.add(new KategorieSummeDTO(entry.getKey(), entry.getValue()));
    }

    return result;
  }

  // die methode gibt die gesamtausgaben pro trip zurueck
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

  // die methode rechnet fuer einen trip: budget - gesamtausgaben
  // falls kein trip mit dem namen gefunden wurde, dann wird -1 zurueckgegeben
  // falls kein budget gesetzt wurde, dann wird -2 zurueckgegeben
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

  public double budget(String tripname, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }

    return userTripRepository.findByUserAndTrip(user, trip).map(UserTrip::getBudget).orElse(0.0);
  }

  public double budgetAktualisieren(String tripname, double budget, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }

    UserTrip userTrip =
        userTripRepository
            .findByUserAndTrip(user, trip)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    userTrip.setBudget(budget);
    userTripRepository.save(userTrip);

    return userTrip.getBudget();
  }

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

  public double warningRangeAktualisieren(
      String tripname, double warningRange, String username) {
    User user = this.getCurrentUserOrThrow(username);
    Trip trip = tripRepository.findByName(tripname);
    if (trip == null) {
      return -1;
    }

    UserTrip userTrip =
        userTripRepository
            .findByUserAndTrip(user, trip)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    userTrip.setWarningRange(warningRange);
    userTripRepository.save(userTrip);

    return userTrip.getWarningRange();
  }

  private User getCurrentUserOrThrow(String username) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, username)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
  }
}

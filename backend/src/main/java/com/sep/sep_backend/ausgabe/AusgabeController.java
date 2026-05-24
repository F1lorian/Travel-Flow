package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201"})
public class AusgabeController {

    private final AusgabeRepository ausgabeRepo;
    private AusgabeService ausgabeService;
    private TripRepository tripRepository;

    @Autowired
    public AusgabeController(AusgabeRepository ausgabeRepo, AusgabeService ausgabeService,  TripRepository tripRepository) {
        this.ausgabeRepo = ausgabeRepo;
        this.ausgabeService = ausgabeService;
        this.tripRepository = tripRepository;
    }

    @PostMapping({"/ausgabe", "/api/ausgabe"})
    public void ausgabeHinzufuegen(@RequestBody AusgabeRequestDTO dto) {
        if (dto.getTrip() == null || dto.getTrip().getName() == null || dto.getTrip().getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip name is required");
        }
        Trip trip = tripRepository.findByName(dto.getTrip().getName());
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        dto.setTrip(trip);
        ausgabeService.ausgabeHinzufuegen(dto);
    }

  @GetMapping({"/ausgabenliste", "/api/ausgabenliste"})
  public List<AusgabeResponseDTO> ausgabenliste(
      @RequestParam LocalDate start, @RequestParam LocalDate end) {
    return ausgabeService.ausgabenliste(start, end);
  }

    @GetMapping({"/proKategorie", "/api/proKategorie"})
    public List<KategorieSummeDTO> ausgabenProKategorie(@RequestParam LocalDate start,
                                                        @RequestParam LocalDate end) {
        return ausgabeService.ausgabenProKategorie(start, end);
    }

    @GetMapping({"/ausgabenlisteTrip", "/api/ausgabenlisteTrip"})
    public List<AusgabeResponseDTO> ausgabenListeTrip(@RequestParam String tripname) {
        List<AusgabeResponseDTO> result = ausgabeService.ausgabenlisteTrip(tripname);
        if  (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return result;
    }

    @GetMapping({"/proKategorieTrip", "/api/proKategorieTrip"})
    public List<KategorieSummeDTO> ausgabenProKategorieTrip(@RequestParam String tripname) {
        List<KategorieSummeDTO> result = ausgabeService.ausgabenProKategorieTrip(tripname);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return result;
    }

    @GetMapping({"/budget", "/api/budget"})
    public double budget(@RequestParam String tripname) {
        Trip trip = tripRepository.findByName(tripname);
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return trip.getBudget();
    }

    @PutMapping({"/budget", "/api/budget"})
    public double budgetAktualisieren(@RequestParam String tripname,
                                      @RequestParam double budget) {
        if (budget < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget must not be negative");
        }

        Trip trip = tripRepository.findByName(tripname);

        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        trip.setBudget(budget);
        tripRepository.save(trip);

        return trip.getBudget();
    }

    @GetMapping({"/gesamtausgabenTrip", "/api/gesamtausgabenTrip"})
    public double gesamtausgabenTrip(@RequestParam String tripname) {
        double result = ausgabeService.gesamtausgabenTrip(tripname);
        if (result < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        return result;
    }

    @GetMapping({"/verfuegbar", "/api/verfuegbar"})
    public double verfuegbar(@RequestParam String tripname) {
        double result = ausgabeService.verfuegbarTrip(tripname);
        if (result == -1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        if(result == -2){
            return 0;
        }
        return result;
    }

}

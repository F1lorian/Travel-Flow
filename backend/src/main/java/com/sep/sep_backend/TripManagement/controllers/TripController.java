package com.sep.sep_backend.TripManagement.controllers;

/* back-front relation: REST endpoints for trips.
   GET /api/trips         → frontend trip-list page (loads all trips)
   GET /api/trips/{id}    → frontend trip-detail page (loads one trip)
   POST /api/trips        → frontend create-trip form */

import com.sep.sep_backend.TripManagement.dto.TripDTO;
import com.sep.sep_backend.TripManagement.services.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/trips")
@CrossOrigin(origins = "*")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public List<TripDTO> getAllTrips() {
        return tripService.getAllTrips()
                .stream()
                .map(TripDTO::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDTO> getTrip(@PathVariable Long id) {
        return tripService.getTrip(id)
                .map(TripDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TripDTO> createTrip(@RequestBody Map<String, String> body) {
        TripDTO dto = TripDTO.from(tripService.createTrip(body.get("name")));
        return ResponseEntity.ok(dto);
    }
}
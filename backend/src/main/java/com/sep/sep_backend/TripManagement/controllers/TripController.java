package com.sep.sep_backend.TripManagement.controllers;

import com.sep.sep_backend.TripManagement.dto.TripDTO;
import com.sep.sep_backend.repos.TripRepository;
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
    private final TripRepository tripRepository;

    public TripController(TripService tripService, TripRepository tripRepository) {
        this.tripService = tripService;
        this.tripRepository = tripRepository;
    }

    @PostMapping
    public ResponseEntity<TripDTO> createTrip(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        TripDTO dto = TripDTO.from(tripService.createTrip(name));
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public List<TripDTO> getAllTrips() {
        return tripRepository.findAll()
                .stream()
                .map(TripDTO::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDTO> getTrip(@PathVariable Long id) {
        return tripRepository.findById(id)
                .map(TripDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
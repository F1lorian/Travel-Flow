package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.AusgabeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class AusgabeController {

    private final AusgabeRepository ausgabeRepo;
    private AusgabeService ausgabeService;

    @Autowired
    public AusgabeController(AusgabeRepository ausgabeRepo, AusgabeService ausgabeService){
        this.ausgabeRepo = ausgabeRepo;
        this.ausgabeService = ausgabeService;
    }

    @PostMapping("/ausgabe")
    public void ausgabeHinzufuegen(@RequestBody AusgabeRequestDTO ausgabeDTO){
        ausgabeService.ausgabeHinzufuegen(ausgabeDTO);
    }

    @GetMapping("/ausgabenliste")
    public List<AusgabeResponseDTO> ausgabenliste( @RequestParam LocalDate start,
                                                   @RequestParam LocalDate end){
        return ausgabeService.ausgabenliste(start, end);
    }

    @GetMapping("/proKategorie")
    public Map<Kategorie, Double> ausgabenProKategorie( @RequestParam LocalDate start,
                                                        @RequestParam LocalDate end){
        return ausgabeService.ausgabenProKategorie(start, end);
    }

}

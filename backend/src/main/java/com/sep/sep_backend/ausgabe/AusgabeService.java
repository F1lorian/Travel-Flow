package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.repos.AusgabeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AusgabeService {

    AusgabeRepository repo;

    @Autowired
    public AusgabeService(AusgabeRepository repo){
        this.repo = repo;
    }

    // Ausgabe hinzufuegen
    public void ausgabeHinzufuegen(AusgabeRequestDTO ausgabeDTO){
        Ausgabe ausgabe = new Ausgabe(ausgabeDTO.getKosten(), ausgabeDTO.getTrip(), ausgabeDTO.getDate(), ausgabeDTO.getKategorie());
        repo.save(ausgabe);
    }

    // die Methode gibt eine Liste von Ausgaben in dem gewählten Interval
    public List<AusgabeResponseDTO> ausgabenliste(LocalDate start, LocalDate end){
        List<Ausgabe> ausgaben = repo.findByDatumBetween(start, end);
        List<AusgabeResponseDTO> ausgabenDTOs = new ArrayList<>();
        for (int i=0; i < ausgaben.size(); i++){
            Ausgabe x = ausgaben.get(i);
            AusgabeResponseDTO y = new AusgabeResponseDTO(x.getKosten(), x.getTrip(), x.getDate(), x.getKategorie());
            ausgabenDTOs.add(y);
        }
        return ausgabenDTOs;
    }


    // die Methode gibt eine Darstellung, in Form eines Maps, von Kategorie und ihre Gesamtkosten in dem gewählten Interval
    public Map<Kategorie, Double> ausgabenProKategorie(LocalDate start, LocalDate end){
        List<Ausgabe> ausgaben = repo.findByDatumBetween(start, end);
        Map<Kategorie, Double> summen = new HashMap<>();

        for (Ausgabe ausgabe : ausgaben) {
            Kategorie kategorie = ausgabe.getKategorie();
            double kosten = ausgabe.getKosten();

            if (summen.containsKey(kategorie)) {
                double alteSumme = summen.get(kategorie);
                summen.put(kategorie, alteSumme + kosten);
            } else {
                summen.put(kategorie, kosten);
            }
        }

        return summen;
    }

    }
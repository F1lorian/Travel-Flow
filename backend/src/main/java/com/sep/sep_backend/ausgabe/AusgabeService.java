package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.AusgabeRepository;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
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
    TripRepository tripRepository;
    EventRepository eventRepository;

    @Autowired
    public AusgabeService(AusgabeRepository repo, TripRepository tripRepository, EventRepository eventRepository) {
        this.repo = repo;
        this.tripRepository = tripRepository;
        this.eventRepository = eventRepository;
    }

    // Ausgabe hinzufuegen
    public void ausgabeHinzufuegen(AusgabeRequestDTO dto){
        Ausgabe ausgabe = new Ausgabe(dto.getKosten(), dto.getTrip(), dto.getDate(), dto.getKategorie());
        repo.save(ausgabe);
    }

    // die Methode gibt eine Liste von Ausgaben in dem gewählten Interval
    public List<AusgabeResponseDTO> ausgabenliste(LocalDate start, LocalDate end){
        List<Ausgabe> ausgaben = repo.findByDateBetween(start, end);
        List<Event> events = eventRepository.findByDateTimeBetween(start.atStartOfDay(), end.atStartOfDay());
        List<AusgabeResponseDTO> ausgabenDTOs = new ArrayList<>();
        for (int i=0; i < ausgaben.size(); i++){
            Ausgabe x = ausgaben.get(i);
            AusgabeResponseDTO y = new AusgabeResponseDTO(x.getKosten(), x.getTrip(), x.getDate(), x.getKategorie());
            ausgabenDTOs.add(y);
        }
        for (int i=0; i < events.size(); i++){
            Event x = events.get(i);
            AusgabeResponseDTO y = new AusgabeResponseDTO(x.getKosten(), x.getTrip(), x.getDateTime().toLocalDate(), Kategorie.SONSTIGES);
            ausgabenDTOs.add(y);
        }
        return ausgabenDTOs;
    }


    // die Methode gibt eine Darstellung, in Form eines Maps, von Kategorie und ihre Gesamtkosten in dem gewählten Interval
    public Map<Kategorie, Double> ausgabenProKategorie(LocalDate start, LocalDate end){
        List<Ausgabe> ausgaben = repo.findByDateBetween(start, end);
        List<Event> events = eventRepository.findByDateTimeBetween(start.atStartOfDay(), end.atStartOfDay());
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
        for (Event event : events) {
            Kategorie kategorie = Kategorie.SONSTIGES;
            double kosten = event.getKosten();

            if (summen.containsKey(kategorie)) {
                double alteSumme = summen.get(kategorie);
                summen.put(kategorie, alteSumme + kosten);
            } else {
                summen.put(kategorie, kosten);
            }
        }

        return summen;
    }

    // Methode liefert Liste von Ausgaben von diesem Trip
    public List<AusgabeResponseDTO> ausgabenlisteTrip(String tripname){
        Trip trip = tripRepository.findByName(tripname);
        if(trip == null){
            return null;
        }
        List<Ausgabe> ausgaben = repo.findByTrip(trip);
        List<Event> events = eventRepository.findByTripId(trip.getId());
        List<AusgabeResponseDTO> ausgabenDTOs = new ArrayList<>();
        for(Ausgabe ausgabe : ausgaben){
            AusgabeResponseDTO dto = new AusgabeResponseDTO(ausgabe.getKosten(), trip, ausgabe.getDate(), ausgabe.getKategorie());
            ausgabenDTOs.add(dto);
        }
        for(Event event : events){
            AusgabeResponseDTO dto = new AusgabeResponseDTO(event.getKosten(), trip, event.getDateTime().toLocalDate(), Kategorie.SONSTIGES);
            ausgabenDTOs.add(dto);
        }
        return ausgabenDTOs;
    }


    // die Methode gibt eine Darstellung, in Form eines Maps, von Kategorie und ihre Gesamtkosten fuer ein bestimmten Trip
    public Map<Kategorie, Double> ausgabenProKategorieTrip(String tripname){
        Trip trip = tripRepository.findByName(tripname);
        if(trip == null){
            return null;
        }
        List<Ausgabe> ausgaben = repo.findByTrip(trip);
        List<Event> events = eventRepository.findByTripId(trip.getId());
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
        for (Event event : events) {
            Kategorie kategorie = Kategorie.SONSTIGES;
            double kosten = event.getKosten();

            if (summen.containsKey(kategorie)) {
                double alteSumme = summen.get(kategorie);
                summen.put(kategorie, alteSumme + kosten);
            } else {
                summen.put(kategorie, kosten);
            }
        }
        return summen;
    }

    // die methode gibt die gesamtausgaben pro trip zurueck
    public double gesamtausgabenTrip(String tripname){
        Trip trip = tripRepository.findByName(tripname);
        if(trip == null){
            return -1;
        }
        double summe = 0;
        List<Ausgabe> ausgaben = trip.getAusgaben();
        List<Event> events = eventRepository.findByTripId(trip.getId());
        for(Ausgabe ausgabe : ausgaben){
            summe = summe + ausgabe.getKosten();
        }
        for(Event event : events){
            summe = summe + event.getKosten();
        }
        return summe;
    }

    // die methode rechnet fuer einen trip: budget - gesamtausgaben
    // falls kein trip mit dem namen gefunden wurde, dann wird -1 zurueckgegeben
    // falls kein budget gesetzt wurde, dann wird -2 zurueckgegeben
    public double verfuegbarTrip(String tripname){
        Trip trip = tripRepository.findByName(tripname);
        if(trip == null){
            return -1;
        }
        double ausgaben = gesamtausgabenTrip(trip.getName());
        double budget = trip.getBudget();

        if(budget == 0){
            return -2;
        }

        return budget - ausgaben;
    }

    }

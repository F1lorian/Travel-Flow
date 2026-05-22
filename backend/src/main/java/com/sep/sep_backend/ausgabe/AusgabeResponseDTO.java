package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.Enitys.Trip;

import java.time.LocalDate;

public class AusgabeResponseDTO {
    private long id;
    private double kosten;
    private String trip;
    private LocalDate date;
    private String kategorie;

    public AusgabeResponseDTO(){}

    public AusgabeResponseDTO(double kosten, Trip trip, LocalDate date, Categories kategorie){
        this.kosten = kosten;
        this.trip = trip != null ? trip.getName() : "";
        this.date = date;
        this.kategorie = kategorie != null ? kategorie.getName() : "SONSTIGES";
    }


    // Getter
    public double getKosten(){
        return this.kosten;
    }

    public String getTrip(){
        return this.trip;
    }

    public LocalDate getDate(){
        return this.date;
    }

    public String getKategorie(){ return this.kategorie; }

    // Setter
    public void setKosten (double kosten){
        this.kosten = kosten;
    }

    public void setTrip(String trip){
        this.trip = trip;
    }

    public void setDate(LocalDate date){
        this.date = date;
    }

    public void setKategorie(String kategorie) {
        this.kategorie = kategorie;
    }
}

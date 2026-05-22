package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.Enitys.Trip;

import java.time.LocalDate;

public class AusgabeRequestDTO {
    private long id;
    private double kosten;
    private Trip trip;
    private LocalDate date;
    private String kategorie;
    public AusgabeRequestDTO() {}

    public AusgabeRequestDTO(double kosten, Trip trip, LocalDate date, String kategorie) {
        this.kosten = kosten;
        this.trip = trip;
        this.date = date;
        this.kategorie = kategorie;
    }

    // Getter
    public double getKosten(){
        return this.kosten;
    }

    public Trip getTrip(){
        return this.trip;
    }

    public LocalDate getDate(){
        return this.date;
    }

    public String getKategorie(){ return this.kategorie; }

    // Setter
    public void setKosten(double kosten){
        this.kosten = kosten;
    }

    public void setTrip(Trip trip){
        this.trip = trip;
    }

    public void setDate(LocalDate date){ this.date = date; }

    public void setKategorie(String kategorie){ this.kategorie = kategorie; }

}

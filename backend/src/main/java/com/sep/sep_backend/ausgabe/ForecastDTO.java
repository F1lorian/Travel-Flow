package com.sep.sep_backend.ausgabe;

public class ForecastDTO {
    private double daily;
    private double week;

    public ForecastDTO(double daily, double week) {
        this.daily = daily;
        this.week = week;
    }

    // Getter und Setter
    public double getDaily() {
        return daily;
    }
    public void setDaily(double daily) {
        this.daily = daily;
    }
    public double getWeek() {
        return week;
    }
    public void setWeek(double week) {
        this.week = week;
    }

}

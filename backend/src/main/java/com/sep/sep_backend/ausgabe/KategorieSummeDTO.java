package com.sep.sep_backend.ausgabe;

public class KategorieSummeDTO {
    private String category;
    private double cost;

    public KategorieSummeDTO(String category, double cost) {
        this.category = category;
        this.cost = cost;
    }

    public String getCategory() {
        return category;
    }

    public double getCost() {
        return cost;
    }
}


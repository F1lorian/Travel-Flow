package com.sep.sep_backend.ausgabe;

/** Aggregated expense total for one category. */
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

package com.sep.sep_backend.TripManagement.dto;

import com.sep.sep_backend.Enitys.Categories;

/** Lightweight view of a {@link Categories} entity sent to the frontend (id + name only). */
public class CategoryDTO {
  private long id;
  private String name;

  // Factory: copy the fields we expose from the entity into a DTO.
  public static CategoryDTO from(Categories c) {
    CategoryDTO dto = new CategoryDTO();
    dto.id = c.getId();
    dto.name = c.getName();
    return dto;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}

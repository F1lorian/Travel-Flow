package com.sep.sep_backend.TripManagement.dto;

import com.sep.sep_backend.entities.Categories;

/**
 * API response shape for a category — only the {@code id} and {@code name} the frontend actually
 * needs.
 *
 * <p>Why a DTO instead of returning the {@link Categories} entity directly:
 *
 * <ul>
 *   <li>keeps the JSON stable even if the entity gains internal fields/relations;
 *   <li>avoids accidentally serializing lazy JPA relationships (and the errors that come with
 *       them).
 * </ul>
 */
public class CategoryDTO {
  private long id;
  private String name;

  /** Builds a DTO from an entity, copying only the fields we expose. */
  public static CategoryDTO from(Categories c) {
    CategoryDTO dto = new CategoryDTO();
    dto.id = c.getId();
    dto.name = c.getName();
    return dto;
  }

  // Getters only: the DTO is read-only output, built via from() above.
  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}

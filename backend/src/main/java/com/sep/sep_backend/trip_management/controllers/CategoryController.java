package com.sep.sep_backend.trip_management.controllers;

import com.sep.sep_backend.trip_management.dto.CategoryDTO;
import com.sep.sep_backend.trip_management.services.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for event categories.
 *
 * <p>Categories are the labels users put on events (e.g. "Food", "Museum"). The frontend uses the
 * "recent" list to power the category autocomplete on the add-event form, so people can reuse
 * existing labels instead of retyping them.
 *
 * <p>{@code @CrossOrigin} allows the Angular dev server (a different origin) to call this API
 * during local development.
 */
@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

  private final CategoryService categoryService;

  // Spring injects the service; the controller only handles HTTP, not data access.
  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /**
   * GET /api/categories/recent — returns the most recently created categories.
   *
   * <p>Each entity is converted to a {@link CategoryDTO} so the JPA entity (with its relationships)
   * is never serialized straight to the client.
   */
  @GetMapping("/recent")
  public List<CategoryDTO> getRecent() {
    return categoryService.getRecent().stream().map(CategoryDTO::from).toList();
  }
}

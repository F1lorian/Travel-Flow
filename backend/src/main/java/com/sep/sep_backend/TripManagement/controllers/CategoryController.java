package com.sep.sep_backend.TripManagement.controllers;

import com.sep.sep_backend.TripManagement.dto.CategoryDTO;
import com.sep.sep_backend.TripManagement.services.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** REST endpoints for event categories — used to suggest recent categories in the UI. */
@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  // GET /api/categories/recent — latest categories, mapped to DTOs for the frontend.
  @GetMapping("/recent")
  public List<CategoryDTO> getRecent() {
    return categoryService.getRecent().stream().map(CategoryDTO::from).toList();
  }
}

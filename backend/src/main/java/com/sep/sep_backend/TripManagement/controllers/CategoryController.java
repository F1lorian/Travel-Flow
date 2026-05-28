package com.sep.sep_backend.TripManagement.controllers;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.TripManagement.dto.CategoryDTO;
import com.sep.sep_backend.TripManagement.services.CategoryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

  public record CreateCategoryRequest(String name) {}

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping("/recent")
  public List<CategoryDTO> getRecent() {
    return categoryService.getRecent().stream().map(CategoryDTO::from).toList();
  }

  @PostMapping
  public ResponseEntity<CategoryDTO> create(@RequestBody CreateCategoryRequest request) {
    Categories saved = categoryService.getOrCreate(request.name());
    return ResponseEntity.ok(CategoryDTO.from(saved));
  }
}

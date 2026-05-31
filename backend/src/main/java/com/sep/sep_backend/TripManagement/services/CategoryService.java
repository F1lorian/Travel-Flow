package com.sep.sep_backend.TripManagement.services;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.repos.CategoriesRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Business logic for event categories: listing recent ones and find-or-create by name. */
@Service
public class CategoryService {

  private final CategoriesRepository categoriesRepository;

  public CategoryService(CategoriesRepository categoriesRepository) {
    this.categoriesRepository = categoriesRepository;
  }

  // The 10 most recently added categories (highest ids first) for the UI suggestions.
  public List<Categories> getRecent() {
    return categoriesRepository.findTop10ByOrderByIdDesc();
  }

  // Returns the existing category with this name (case-insensitive), or creates and
  // saves a new one. Keeps category names unique without duplicating on capitalisation.
  public Categories getOrCreate(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Category name cannot be empty");
    }
    String trimmed = name.trim();
    return categoriesRepository
        .findByNameIgnoreCase(trimmed)
        .orElseGet(
            () -> {
              Categories fresh = new Categories();
              fresh.setName(trimmed);
              return categoriesRepository.save(fresh);
            });
  }
}

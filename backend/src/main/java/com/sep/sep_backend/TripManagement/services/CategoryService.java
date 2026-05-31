package com.sep.sep_backend.TripManagement.services;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.repos.CategoriesRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Business logic for event categories. Sits between the controller and the database ({@link
 * CategoriesRepository}) so the controller stays HTTP-only.
 */
@Service
public class CategoryService {

  private final CategoriesRepository categoriesRepository;

  public CategoryService(CategoriesRepository categoriesRepository) {
    this.categoriesRepository = categoriesRepository;
  }

  /**
   * The 10 newest categories, newest first. Highest id = most recently created, so ordering by id
   * descending gives "recent" without a timestamp column.
   */
  public List<Categories> getRecent() {
    return categoriesRepository.findTop10ByOrderByIdDesc();
  }

  /**
   * Finds the category with this name, or creates it if it doesn't exist yet.
   *
   * <p>Called when a user adds an event and types a category name. The lookup is case-insensitive
   * and trimmed so "Food", "food " and "FOOD" all resolve to the same row instead of creating
   * duplicates. Blank names are rejected.
   */
  public Categories getOrCreate(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Category name cannot be empty");
    }
    String trimmed = name.trim();
    return categoriesRepository
        .findByNameIgnoreCase(trimmed)
        // orElseGet runs only when no match was found — create and save the new one.
        .orElseGet(
            () -> {
              Categories fresh = new Categories();
              fresh.setName(trimmed);
              return categoriesRepository.save(fresh);
            });
  }
}

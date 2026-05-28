package com.sep.sep_backend.TripManagement.services;

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.repos.CategoriesRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

  private final CategoriesRepository categoriesRepository;

  public CategoryService(CategoriesRepository categoriesRepository) {
    this.categoriesRepository = categoriesRepository;
  }

  public List<Categories> getRecent() {
    return categoriesRepository.findTop10ByOrderByIdDesc();
  }

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

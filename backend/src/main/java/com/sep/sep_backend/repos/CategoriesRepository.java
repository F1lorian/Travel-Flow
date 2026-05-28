package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Categories;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriesRepository extends JpaRepository<Categories, Long> {
  Optional<Categories> findByName(String name);
}

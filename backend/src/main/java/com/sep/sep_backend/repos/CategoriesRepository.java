package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Categories;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriesRepository extends JpaRepository<Categories, Long> {

  List<Categories> findTop10ByOrderByIdDesc();

  Optional<Categories> findByNameIgnoreCase(String name);

  Optional<Categories> findByName(String name);
}

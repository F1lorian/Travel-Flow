package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Categories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriesRepository extends JpaRepository<Categories, Long> {
    Optional<Categories> findByName(String name);
}

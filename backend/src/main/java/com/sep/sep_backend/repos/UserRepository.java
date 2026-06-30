package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  Optional<User> findByName(String name);

  boolean existsByEmail(String email);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByEmailIgnoreCase(String email);

  Optional<User> findByEmailIgnoreCase(String email);

  Optional<User> findByNameIgnoreCaseOrEmailIgnoreCase(String name, String email);

  List<User>
      findByNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(
          String name, String firstName, String surname);
}

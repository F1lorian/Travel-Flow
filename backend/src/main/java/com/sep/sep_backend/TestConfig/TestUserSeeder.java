package com.sep.sep_backend.TestConfig;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("test-users")
public class TestUserSeeder {

  @Bean
  CommandLineRunner seedTestUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    return args -> {
      createUserIfMissing(
          userRepository,
          passwordEncoder,
          "testuser",
          "testuser@example.com",
          "Test1234!",
          "Test",
          "User");
    };
  }

  private void createUserIfMissing(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      String username,
      String email,
      String password,
      String firstName,
      String surname) {
    if (userRepository.existsByNameIgnoreCase(username)
        || userRepository.existsByEmailIgnoreCase(email)) {
      return;
    }

    User user =
        new User(
            username,
            email,
            passwordEncoder.encode(password),
            passwordEncoder.encode("blue"));
    user.setFirstName(firstName);
    user.setSurname(surname);
    user.setSecurityQuestion("What is your favorite color?");
    user.setEmailVerified(true);

    userRepository.save(user);
  }

}

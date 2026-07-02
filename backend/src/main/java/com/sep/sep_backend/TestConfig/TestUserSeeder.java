package com.sep.sep_backend.TestConfig;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Profile("test-users")
public class TestUserSeeder {

  @Bean
  CommandLineRunner seedTestUsers(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PlatformTransactionManager transactionManager) {
    return args -> {
      TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
      transactionTemplate.executeWithoutResult(
          status -> {
            User testUser =
                createUserIfMissing(
                    userRepository,
                    passwordEncoder,
                    "testuser",
                    "testuser@example.com",
                    "Test1234!",
                    "Test",
                    "User");

            User friendUser =
                createUserIfMissing(
                    userRepository,
                    passwordEncoder,
                    "frienduser",
                    "frienduser@example.com",
                    "Test1234!",
                    "Friend",
                    "User");

            createUserIfMissing(
                userRepository,
                passwordEncoder,
                "strangeruser",
                "strangeruser@example.com",
                "Test1234!",
                "Stranger",
                "User");

            addFriendshipIfMissing(testUser, friendUser);
            userRepository.save(testUser);
            userRepository.save(friendUser);
          });
    };
  }

  private User createUserIfMissing(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      String username,
      String email,
      String password,
      String firstName,
      String surname) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, email)
        .orElseGet(
            () -> {
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

              return userRepository.save(user);
            });
  }

  private void addFriendshipIfMissing(User user, User friend) {
    if (!hasFriend(user, friend)) {
      user.getFriends().add(friend);
    }
    if (!hasFriend(friend, user)) {
      friend.getFriends().add(user);
    }
  }

  private boolean hasFriend(User user, User friend) {
    return user.getFriends().stream()
        .anyMatch(existingFriend -> existingFriend.getId() == friend.getId());
  }
}

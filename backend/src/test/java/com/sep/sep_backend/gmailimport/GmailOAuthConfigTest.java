package com.sep.sep_backend.gmailimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Guards the spring-dotenv wiring that makes the Gmail OAuth config resolvable. Without it {@code
 * google.oauth.client-secret} is blank and {@link GmailOAuthService} reports "Gmail import is not
 * configured".
 */
class GmailOAuthConfigTest {

  @SpringBootConfiguration
  static class EmptyConfig {}

  /**
   * spring-dotenv is configured through a classpath {@code .env.properties}, not
   * application.properties. This asserts it exists and points at the repo root (one level above the
   * backend working directory) so the shared {@code .env} is loaded.
   */
  @Test
  void dotenvIsConfiguredToReadTheRepoRootEnv() throws Exception {
    Properties props = new Properties();
    try (InputStream in = getClass().getResourceAsStream("/.env.properties")) {
      assertThat(in).as(".env.properties present on the classpath").isNotNull();
      props.load(in);
    }
    assertThat(props.getProperty("directory")).as("spring-dotenv directory").isEqualTo("..");
  }

  /**
   * End-to-end check that the repo-root {@code .env} actually resolves the Gmail OAuth secret.
   * Skipped when no local {@code .env} is present (e.g. CI), since spring-dotenv ignores a missing
   * file.
   */
  @Test
  void gmailOAuthSecretResolvesWhenLocalEnvPresent() {
    assumeTrue(Files.exists(Path.of("..", ".env")), "repo-root .env present");

    try (ConfigurableApplicationContext ctx =
        new SpringApplicationBuilder(EmptyConfig.class).web(WebApplicationType.NONE).run()) {
      assertThat(ctx.getEnvironment().getProperty("google.oauth.client-secret"))
          .as("google.oauth.client-secret resolved from the repo-root .env")
          .isNotBlank();
    }
  }
}

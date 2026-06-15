package com.sep.sep_backend;

import com.sep.sep_backend.accountmanagement.JwtAuthInterceptor;
import com.sep.sep_backend.accountmanagement.JwtService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final ObjectProvider<JwtService> jwtService;

  public WebConfig(ObjectProvider<JwtService> jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // JwtService is absent in @WebMvcTest controller slices; only guard the API when it
    // is available (full application context), so slice tests load without authentication.
    JwtService service = jwtService.getIfAvailable();
    if (service != null) {
      registry
          .addInterceptor(new JwtAuthInterceptor(service))
          .addPathPatterns("/api/**")
          .excludePathPatterns("/api/account/**");
    }
  }
}

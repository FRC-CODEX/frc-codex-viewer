package com.frc.codex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import com.frc.codex.properties.FilingIndexProperties;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String ROLE_USER = "USER";

    private boolean isAnonymousAccess(FilingIndexProperties properties) {
        return !StringUtils.hasText(properties.httpUsername()) || 
               !StringUtils.hasText(properties.httpPassword());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, FilingIndexProperties properties) throws Exception {
        http.headers(headersConfig -> headersConfig.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        // Prevent CSRF from creating cookies.
        http.csrf(CsrfConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (isAnonymousAccess(properties)) {
            logger.info("Anonymous access enabled");
            http.authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll());
        } else {
            logger.info("Authenticated access enabled");
            http.authorizeHttpRequests((authorize) -> authorize
                    .requestMatchers("/health").permitAll()
                    .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder, FilingIndexProperties properties) {
        if (isAnonymousAccess(properties)) {
            logger.info("Creating anonymous user details service");
            return new InMemoryUserDetailsManager();
        }

        logger.info("Creating authenticated user details service");
        UserDetails userDetails = User.builder()
            .username(properties.httpUsername())
            .password(properties.httpPassword())
            .passwordEncoder(passwordEncoder::encode)
            .roles(ROLE_USER)
            .build();
        return new InMemoryUserDetailsManager(userDetails);
    }
}

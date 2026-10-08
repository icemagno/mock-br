package br.gov.defesa.mockgovbr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Desabilita CSRF para permitir POST direto em /token, /userinfo e chamadas REST
            .csrf(AbstractHttpConfigurer::disable)
            // Libera todos os endpoints necessários para o fluxo OIDC
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/authorize",
                    "/token",
                    "/userinfo",
                    "/jwks.json",
                    "/.well-known/**",
                    "/api/**",
                    "/error",
                    "/css/**",
                    "/js/**",
                    "/images/**"
                ).permitAll()
                .anyRequest().permitAll()
            )
            // Desabilita login padrão do Spring Security pois o formulário /authorize gerencia a autenticação
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}

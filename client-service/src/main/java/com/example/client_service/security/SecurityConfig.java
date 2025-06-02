package com.example.client_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // 🔹 Deshabilitar CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // 🔹 Configuración CORS actualizada
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 🔥 ¡AGREGAR ESTO!
                        .pathMatchers(HttpMethod.DELETE, "/api/clients/**").permitAll() // ✅ Permitir solo DELETE sin JWT
                        .pathMatchers(HttpMethod.GET, "/api/payments/approve-payment", "/api/payments/approve-payment/**").permitAll()
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/clients",
                                "/api/sorteos",
                                "/api/ganadores",
                                "/api/clients/approve/**",
                                "/api/payments/approve-payment",
                                "/api/payments/approve-payment/**"
                        ).permitAll()
                        .pathMatchers("/api/menu").authenticated()
                        .pathMatchers("/api/clients/admin/**").hasRole("ADMINISTRADOR")
                        .pathMatchers("/api/clients/perfil").hasAnyRole("ADMINISTRADOR", "PARTICIPANTE")
                        .pathMatchers("/api/clients/**").authenticated()
                        .anyExchange().authenticated()
                )

                .addFilterAt(new JwtAuthenticationFilter(jwtUtil), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }


    @Bean
    public CorsWebFilter corsWebFilter() {
        return new CorsWebFilter(corsConfigurationSource());
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://sortsortech.azurewebsites.net"
        ));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("*")); // 🛠️ Permitir todos los headers para OPTIONS
        corsConfig.setExposedHeaders(List.of("Authorization", "Content-Type"));
        corsConfig.setAllowCredentials(true); // 🔐 Para permitir cookies o token si se usa

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }


}

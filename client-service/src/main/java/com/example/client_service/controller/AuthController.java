package com.example.client_service.controller;

import com.example.client_service.security.AuthService;
import com.example.client_service.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000") // 🔹 Permitir solo frontend
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody Map<String, String> request) {
        String dni = request.get("dni");
        String password = request.get("password");

        if (dni == null || password == null) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Faltan parámetros: dni y password")));
        }

        return authService.login(dni, password)
                .flatMap(client -> {
                    String token = jwtUtil.generateToken(client.getDni(), client.getNombres(), client.getRol());
                    System.out.println("🔹 Token generado: " + token);

                    Map<String, String> response = Map.of(
                            "token", token,
                            "message", "Inicio de sesión exitoso"
                    );

                    return Mono.just(ResponseEntity.ok().body(response));
                })
                .defaultIfEmpty(ResponseEntity.status(401).body(Map.of("error", "Credenciales incorrectas")));
    }
}

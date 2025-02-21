package com.example.client_service.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    // 🔹 Clave secreta fija (mínimo 32 bytes para HS256)
    private static final String SECRET_KEY = "ClaveSuperSeguraDeMasDe32BytesParaJWT!";

    private static final long EXPIRATION_TIME = 86400000; // 24 horas (1 día)

    private final Key key;

    public JwtUtil() {
        // 🔹 Convertir la clave en bytes asegurando que tenga el formato correcto
        this.key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    /**
     * 🔹 Genera un token JWT válido usando HS256
     * @param dni DNI del usuario (se usa como subject)
     * @param nombres Nombre del usuario
     * @param rol Rol del usuario
     * @return Token JWT firmado
     */
    public String generateToken(String dni, String nombres, String rol) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", nombres);
        claims.put("rol", rol);

        return Jwts.builder()
                .setHeaderParam("typ", "JWT") // Header estándar
                .setClaims(claims)            // Payload con datos del usuario
                .setSubject(dni)              // Subject es el DNI del usuario
                .setIssuedAt(new Date())      // Fecha de emisión
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // Expiración del token
                .signWith(key, SignatureAlgorithm.HS256) // Firmar el token con la clave segura
                .compact();
    }

    /**
     * 🔹 Valida el token JWT
     * @param token Token a validar
     * @return `true` si el token es válido, `false` si está expirado o es inválido
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            System.out.println("✅ Token válido");
            return true;
        } catch (ExpiredJwtException e) {
            System.out.println("🔴 Token expirado: " + e.getMessage());
        } catch (JwtException e) {
            System.out.println("🔴 Token inválido: " + e.getMessage());
        }
        return false;
    }

    /**
     * 🔹 Extrae los claims del token JWT
     * @param token Token JWT
     * @return Claims (datos del payload)
     */
    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 🔹 Extrae el DNI (subject) del token
     */
    public String extractSubject(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * 🔹 Extrae el nombre del usuario del token
     */
    public String extractName(String token) {
        return extractClaims(token).get("name", String.class);
    }

    /**
     * 🔹 Extrae el rol del usuario del token
     */
    public String extractRole(String token) {
        return extractClaims(token).get("rol", String.class);
    }
}

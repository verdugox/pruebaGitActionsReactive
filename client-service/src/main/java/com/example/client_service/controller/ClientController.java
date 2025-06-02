package com.example.client_service.controller;

import com.example.client_service.model.Client;
import com.example.client_service.security.JwtUtil;
import com.example.client_service.service.ClientService;
import com.example.client_service.service.MenuService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = {
        "http://localhost:3000",
        "https://sortsortech.azurewebsites.net"
})
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService service;
    private final JwtUtil jwtUtil;
    private final MenuService menuService; // 🔹 Asegurar que está declarado

    public ClientController(ClientService service, JwtUtil jwtUtil, MenuService menuService) {
        this.service = service;
        this.jwtUtil = jwtUtil;
        this.menuService = menuService;
    }

    @GetMapping
    public Flux<Client> getAllClients() {
        return service.getAllClients();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Client>> getClientById(@PathVariable String id) {
        return service.getClientById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Object>> createClient(@Valid @RequestBody Client client) {
        if (client.getVoucherUrl() == null || client.getVoucherUrl().isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El voucher de pago es obligatorio.")));
        }

        return service.saveClient(client)
                .map(savedClient -> ResponseEntity.status(HttpStatus.CREATED).body((Object) savedClient))
                .onErrorResume(IllegalArgumentException.class, e -> {
                    log.error("Error de validación: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", e.getMessage())));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Error inesperado en el servidor: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Error interno del servidor. Detalles: " + e.getMessage())));
                });
    }





    @PutMapping("/{id}")
    public Mono<ResponseEntity<Client>> updateClient(@PathVariable String id, @RequestBody Client client) {
        return service.updateClient(id, client);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteClient(@PathVariable String id) {
        return service.deleteClient(id);
    }

    @GetMapping("/approve/{id}")
    public Mono<ResponseEntity<Client>> approveClient(@PathVariable String id) {
        return service.approveClient(id);
    }

    /**
     * Enviar email con el link de login
     */
    @PostMapping("/send-login-email/{id}")
    public Mono<ResponseEntity<String>> sendLoginEmail(@PathVariable String id) {
        return service.getClientById(id)
                .flatMap(client -> service.sendLoginEmail(client)
                        .then(Mono.just(ResponseEntity.ok("Correo enviado"))))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Obtener perfil del usuario autenticado a partir del JWT
     */
    @GetMapping("/perfil")
    public Mono<ResponseEntity<Map<String, Object>>> getPerfil(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(token -> token.startsWith("Bearer "))
                .map(token -> token.substring(7)) // Quitar "Bearer "
                .flatMap(token -> {
                    if (!jwtUtil.validateToken(token)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }

                    String dni = jwtUtil.extractSubject(token);

                    return service.getClientByDni(dni)
                            .flatMap(client -> menuService.getMenuByRol(client.getRol()) // Buscar el menú en MongoDB
                                    .map(menu -> {
                                        Map<String, Object> response = new HashMap<>();
                                        response.put("perfil", client);
                                        response.put("menu", menu.getItems()); // Solo devolver los items del menú
                                        return ResponseEntity.ok(response);
                                    }))
                            .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                });
    }

    @PostMapping("/send-mass-email")
    public Mono<ResponseEntity<String>> sendMassEmail() {
        return service.sendMassEmail()
                .then(Mono.just(ResponseEntity.ok("Correos enviados exitosamente")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al enviar correos: " + e.getMessage())));
    }


    @GetMapping("/test-subscription-check")
    public Mono<ResponseEntity<String>> testSubscriptionCheck() {
        return service.checkSubscriptionStatus()
                .then(Mono.just(ResponseEntity.ok("Verificación de suscripciones ejecutada y finalizada correctamente.")));
    }

    @GetMapping("/test-subscription-check2")
    public Mono<ResponseEntity<String>> testSubscriptionCheck2() {
        return service.checkSubscriptionStatus2()
                .then(Mono.just(ResponseEntity.ok("Verificación de suscripciones ejecutada y finalizada correctamente.")));
    }

    @PostMapping("/send-dynamic-mass-email")
    public Mono<ResponseEntity<String>> sendDynamicMassEmail(@RequestBody Map<String, Object> request) {
        String subject = (String) request.get("subject");
        String message = (String) request.get("message");
        List<String> imageUrls = (List<String>) request.get("imageUrls"); // Lista de imágenes de Cloudinary

        return service.sendDynamicMassEmail(subject, message, imageUrls)
                .then(Mono.just(ResponseEntity.ok("Correos masivos enviados exitosamente")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error al enviar correos masivos: " + e.getMessage())));
    }

    @PostMapping("/send-winner-notification")
    public Mono<ResponseEntity<String>> sendWinnerNotification(@RequestBody Map<String, Object> request) {
        String codigoSortec = (String) request.get("codigoSortec");
        String subject = (String) request.get("subject");
        String message = (String) request.get("message");
        List<String> imageUrls = (List<String>) request.get("imageUrls"); // Lista de imágenes de Cloudinary

        return service.sendWinnerNotification(codigoSortec, subject, message, imageUrls)
                .then(Mono.just(ResponseEntity.ok("Correo enviado exitosamente al ganador")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error al enviar correo al ganador: " + e.getMessage())));
    }

    @PostMapping("/send-manual-subscription-reminder")
    public Mono<ResponseEntity<String>> sendManualSubscriptionReminder(@RequestBody Map<String, Object> request) {
        String subject = (String) request.get("subject");
        String message = (String) request.get("message");
        List<String> imageUrls = (List<String>) request.get("imageUrls");

        return service.sendManualSubscriptionReminder(subject, message, imageUrls)
                .then(Mono.just(ResponseEntity.ok("Correos de recordatorio de suscripción enviados exitosamente.")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error al enviar recordatorios: " + e.getMessage())));
    }

    @PostMapping("/send-mass-whatsapp")
    public Mono<ResponseEntity<String>> sendMassWhatsApp(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("El mensaje no puede estar vacío"));
        }

        return service.sendMassWhatsAppMessage(message)
                .then(Mono.just(ResponseEntity.ok("Mensajes enviados exitosamente por WhatsApp.")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500)
                        .body("Error al enviar mensajes de WhatsApp: " + e.getMessage())));
    }







}

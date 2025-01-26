package com.example.client_service.controller;

import com.example.client_service.model.Client;
import com.example.client_service.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
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

    /**
     * Registra un cliente con la URL de Cloudinary ya generada desde el frontend.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Client>> createClient(@RequestBody Client client) {
        if (client.getVoucherUrl() == null || client.getVoucherUrl().isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null));
        }

        return service.saveClient(client)
                .map(savedClient -> ResponseEntity.status(HttpStatus.CREATED).body(savedClient))
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null));
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
}

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
                .map(client -> ResponseEntity.ok(client))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Client>> createClient(@RequestBody Client client) {
        return service.validarPago(client.getReferenciaPago())
                .flatMap(validado -> {
                    if (!validado) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return service.saveClient(client)
                            .map(savedClient -> ResponseEntity.status(HttpStatus.CREATED).body(savedClient));
                });
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteClient(@PathVariable String id) {
        return service.deleteClient(id);
    }
}
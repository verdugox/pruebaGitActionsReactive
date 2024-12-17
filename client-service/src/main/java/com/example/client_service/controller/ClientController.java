package com.example.client_service.controller;

import com.example.client_service.model.Client;
import com.example.client_service.service.ClientService;
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
    public Mono<Client> getClientById(@PathVariable String id) {
           return service.getClientById(id);
    }
    @PostMapping
    public Mono<Client> createClient(@RequestBody Client client){
        return service.saveClient(client);
    }
    @DeleteMapping
    public Mono<Void> deleteClient(@PathVariable String id){
        return service.deleteClient(id);
    }

}

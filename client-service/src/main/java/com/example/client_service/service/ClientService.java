package com.example.client_service.service;

import com.example.client_service.model.Client;
import com.example.client_service.repository.ClientRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ClientService {

     private final ClientRepository repository;

    public ClientService(ClientRepository repository) {
        this.repository = repository;
    }

    public Flux<Client> getAllClients() {
        return repository.findAll();
    }

    public Mono<Client> getClientById(String id) {
        return repository.findById(id);
    }

    public Mono<Boolean> validarPago(String referenciaPago) {
        // Simulación de validación de pagos (API de Yape o Plin puede integrarse aquí)
        return Mono.just(referenciaPago != null && !referenciaPago.isEmpty());
    }

    public Mono<Client> saveClient(Client client) {
        return repository.save(client);
    }

    public Mono<Void> deleteClient(String id) {
        return repository.deleteById(id);
    }
}

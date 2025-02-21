package com.example.client_service.security;

import com.example.client_service.model.Client;
import com.example.client_service.repository.ClientRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final ClientRepository repository;

    public AuthService(ClientRepository repository) {
        this.repository = repository;
    }

    public Mono<Client> login(String dni, String password) {
        return repository.findAll()
                .filter(client -> client.getDni().equals(dni) && client.getCodigoSortec().equals(password))
                .singleOrEmpty();
    }
}
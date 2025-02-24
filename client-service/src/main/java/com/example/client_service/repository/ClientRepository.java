package com.example.client_service.repository;

import com.example.client_service.model.Client;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ClientRepository extends ReactiveMongoRepository<Client, String> {
    Mono<Client> findByDni(String dni);
    Mono<Client> findByCodigoSortecIgnoreCase(String codigoSortec);

    Mono<Client> findByDniOrCorreo(String dni, String correo);


}

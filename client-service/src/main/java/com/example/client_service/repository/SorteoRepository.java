package com.example.client_service.repository;

import com.example.client_service.model.Sorteo;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface SorteoRepository extends ReactiveMongoRepository<Sorteo, String> {
    Flux<Sorteo> findByEstado(String estado);
}

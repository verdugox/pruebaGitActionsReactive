package com.example.client_service.repository;

import com.example.client_service.model.Ganador;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface GanadorRepository extends ReactiveMongoRepository<Ganador, String> {
}

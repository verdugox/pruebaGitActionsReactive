package com.example.client_service.repository;

import com.example.client_service.model.Menu;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface MenuRepository extends ReactiveMongoRepository<Menu, String> {
    Mono<Menu> findByRol(String rol);
}

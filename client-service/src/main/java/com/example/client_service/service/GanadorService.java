package com.example.client_service.service;

import com.example.client_service.config.CircuitBreakerFallbackHandler;
import com.example.client_service.model.Ganador;
import com.example.client_service.repository.GanadorRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GanadorService {

    private final GanadorRepository repository;

    @Autowired
    private CircuitBreakerFallbackHandler fallbackHandler;

    public GanadorService(GanadorRepository repository) {
        this.repository = repository;
    }

    @CircuitBreaker(name = "ganadorServiceCB", fallbackMethod = "getAllGanadoresFallback")
    public Flux<Ganador> getAllGanadores() {
        return repository.findAll()
                .cache(); // ✅ caché local en memoria
    }

    public Flux<Ganador> getAllGanadoresFallback(Throwable t) {
        fallbackHandler.fallbackList(t); // para registrar el error
        return Flux.empty(); // ⚠️ para mantener la respuesta reactiva sin romper
    }

    public Mono<Ganador> getGanadorById(String id) {
        return repository.findById(id);
    }

    public Mono<Ganador> createGanador(Ganador ganador) {
        return repository.save(ganador);
    }

    public Mono<Ganador> updateGanador(String id, Ganador updatedGanador) {
        return repository.findById(id)
                .flatMap(existingGanador -> {
                    existingGanador.setTitulo(updatedGanador.getTitulo());
                    existingGanador.setDescripcion(updatedGanador.getDescripcion());
                    existingGanador.setImagenUrl(updatedGanador.getImagenUrl());
                    existingGanador.setFechaGanador(updatedGanador.getFechaGanador());
                    return repository.save(existingGanador);
                });
    }

    public Mono<Void> deleteGanador(String id) {
        return repository.deleteById(id);
    }
}

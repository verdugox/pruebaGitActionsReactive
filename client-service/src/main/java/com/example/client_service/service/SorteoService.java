package com.example.client_service.service;

import com.example.client_service.config.CircuitBreakerFallbackHandler;
import com.example.client_service.model.Sorteo;
import com.example.client_service.repository.SorteoRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class SorteoService {

    private final SorteoRepository repository;

    @Autowired
    private CircuitBreakerFallbackHandler fallbackHandler;

    public SorteoService(SorteoRepository repository) {
        this.repository = repository;
    }

    @CircuitBreaker(name = "sorteoServiceCB", fallbackMethod = "getAllSorteosFallback")
    public Flux<Sorteo> getAllSorteos() {
        return repository.findAll().cache(); // ✅ cache reactivo
    }

    public Flux<Sorteo> getAllSorteosFallback(Throwable t) {
        fallbackHandler.fallbackList(t);
        return Flux.empty();
    }


    public Mono<Sorteo> getSorteoById(String id) {
        return repository.findById(id);
    }

    @CircuitBreaker(name = "sorteoServiceCB", fallbackMethod = "getSorteosActivosFallback")
    public Flux<Sorteo> getSorteosActivos() {
        return repository.findByEstado("activo")
                .cache(); // ✅ almacena en memoria temporal
    }

    public Flux<Sorteo> getSorteosActivosFallback(Throwable t) {
        fallbackHandler.fallbackList(t); // Solo para log
        return Flux.empty(); // ⚠️ No hay datos pero no rompe
    }

    public Mono<Sorteo> createSorteo(Sorteo sorteo) {
        return repository.save(sorteo);
    }

    public Mono<Sorteo> updateSorteo(String id, Sorteo updatedSorteo) {
        return repository.findById(id)
                .flatMap(existingSorteo -> {
                    existingSorteo.setTitulo(updatedSorteo.getTitulo());
                    existingSorteo.setDescripcion(updatedSorteo.getDescripcion());
                    existingSorteo.setImagenUrl(updatedSorteo.getImagenUrl());
                    existingSorteo.setFechaSorteo(updatedSorteo.getFechaSorteo());
                    existingSorteo.setEstado(updatedSorteo.getEstado());
                    return repository.save(existingSorteo);
                });
    }

    public Mono<Void> deleteSorteo(String id) {
        return repository.deleteById(id);
    }
}

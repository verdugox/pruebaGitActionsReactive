package com.example.client_service.service;

import com.example.client_service.model.Sorteo;
import com.example.client_service.repository.SorteoRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SorteoService {

    private final SorteoRepository repository;

    public SorteoService(SorteoRepository repository) {
        this.repository = repository;
    }

    public Flux<Sorteo> getAllSorteos() {
        return repository.findAll();
    }

    public Mono<Sorteo> getSorteoById(String id) {
        return repository.findById(id);
    }

    public Flux<Sorteo> getSorteosActivos() {
        return repository.findByEstado("activo");
    }

    public Mono<Sorteo> createSorteo(Sorteo sorteo) {
        return repository.save(sorteo);
    }

    public Mono<Sorteo> updateSorteo(String id, Sorteo updatedSorteo) {
        return repository.findById(id)
                .flatMap(existingSorteo -> {
                    existingSorteo.setTitulo(updatedSorteo.getTitulo());
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

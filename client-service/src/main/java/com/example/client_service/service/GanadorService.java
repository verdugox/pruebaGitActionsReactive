package com.example.client_service.service;

import com.example.client_service.model.Ganador;
import com.example.client_service.repository.GanadorRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GanadorService {

    private final GanadorRepository repository;

    public GanadorService(GanadorRepository repository) {
        this.repository = repository;
    }

    public Flux<Ganador> getAllGanadores() {
        return repository.findAll();
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

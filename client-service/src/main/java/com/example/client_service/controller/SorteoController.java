package com.example.client_service.controller;

import com.example.client_service.model.Sorteo;
import com.example.client_service.service.SorteoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/sorteos")
public class SorteoController {

    private final SorteoService service;

    public SorteoController(SorteoService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<Sorteo> getAllSorteos() {
        return service.getAllSorteos();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Sorteo>> getSorteoById(@PathVariable String id) {
        return service.getSorteoById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/activos")
    public Flux<Sorteo> getSorteosActivos() {
        return service.getSorteosActivos();
    }

    @PostMapping
    public Mono<ResponseEntity<Sorteo>> createSorteo(@RequestBody Sorteo sorteo) {
        return service.createSorteo(sorteo)
                .map(savedSorteo -> ResponseEntity.ok(savedSorteo));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Sorteo>> updateSorteo(@PathVariable String id, @RequestBody Sorteo sorteo) {
        return service.updateSorteo(id, sorteo)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteSorteo(@PathVariable String id) {
        return service.deleteSorteo(id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}

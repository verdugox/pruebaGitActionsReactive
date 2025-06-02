package com.example.client_service.controller;

import com.example.client_service.model.Ganador;
import com.example.client_service.service.GanadorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CrossOrigin(origins = {
        "http://localhost:3000",
        "https://sortsortech.azurewebsites.net"
})
@RestController
@RequestMapping("/api/ganadores")
public class GanadorController {

    private final GanadorService service;

    public GanadorController(GanadorService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<Ganador> getAllGanadores() {
        return service.getAllGanadores();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Ganador>> getGanadorById(@PathVariable String id) {
        return service.getGanadorById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Mono<ResponseEntity<Ganador>> createGanador(@RequestBody Ganador ganador) {
        return service.createGanador(ganador)
                .map(savedGanador -> ResponseEntity.ok(savedGanador));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Ganador>> updateGanador(@PathVariable String id, @RequestBody Ganador ganador) {
        return service.updateGanador(id, ganador)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteGanador(@PathVariable String id) {
        return service.deleteGanador(id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}

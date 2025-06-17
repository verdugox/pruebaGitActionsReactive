package com.example.client_service.config;

import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CircuitBreakerFallbackHandler {

    public <T> List<T> fallbackList(Throwable t) {
        System.out.println("Fallback activado: " + t.getMessage());
        return Collections.emptyList();
    }

    public <T> T fallbackSingle(Throwable t) {
        System.out.println("Fallback activado: " + t.getMessage());
        return null;
    }

    public String fallbackString(Throwable t) {
        System.out.println("Fallback activado: " + t.getMessage());
        return "Servicio temporalmente no disponible";
    }

    public <T> Flux<T> fallbackFlux(Throwable t) {
        System.out.println("Circuit Breaker fallbackFlux: " + t.getMessage());
        return Flux.empty();
    }

    public <T> Mono<T> fallbackMono(Throwable t) {
        System.out.println("Circuit Breaker fallbackMono: " + t.getMessage());
        return Mono.empty();
    }
}
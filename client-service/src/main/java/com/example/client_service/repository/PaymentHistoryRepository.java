package com.example.client_service.repository;

import com.example.client_service.model.PaymentHistory;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentHistoryRepository extends ReactiveMongoRepository<PaymentHistory, String> {
    Flux<PaymentHistory> findByClientId(String clientId);

    Flux<PaymentHistory> findByDni(String dni);

    // 🔹 Buscar el último pago con estado "pendiente" de un cliente
    @Query("{ 'clientId': ?0, 'estado': 'pendiente' }")
    Mono<PaymentHistory> findLastPendingPayment(String clientId);
}

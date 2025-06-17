package com.example.client_service.service;

import com.example.client_service.config.CircuitBreakerFallbackHandler;
import com.example.client_service.model.PaymentHistory;
import com.example.client_service.repository.PaymentHistoryRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PaymentHistoryService {
    private final PaymentHistoryRepository repository;

    @Autowired
    private CircuitBreakerFallbackHandler fallbackHandler;

    public PaymentHistoryService(PaymentHistoryRepository repository) {
        this.repository = repository;
    }

    public Mono<PaymentHistory> registerPayment(String clientId, String dni, double monto, String voucherUrl) {
        return repository.findByClientId(clientId)
                .collectList()
                .flatMap(payments -> {
                    PaymentHistory newPayment = new PaymentHistory();
                    newPayment.setClientId(clientId);
                    newPayment.setDni(dni);
                    newPayment.setVoucherUrl(voucherUrl);
                    newPayment.setMonto(monto);
                    newPayment.setEstado("pendiente");
                    newPayment.setFechaPago(ZonedDateTime.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

                    return repository.save(newPayment);
                });
    }

    // ✅ Nuevo método para obtener todos los pagos
    @CircuitBreaker(name = "paymentHistoryServiceCB", fallbackMethod = "getAllPaymentsFallback")
    @Cacheable("payments")
    public Flux<PaymentHistory> getAllPayments() {
        return repository.findAll();
    }

    // Fallback para getAllPayments
    public Flux<PaymentHistory> getAllPaymentsFallback(Throwable t) {
        return fallbackHandler.fallbackFlux(t);
    }

    public Flux<PaymentHistory> getPaymentsByClientId(String clientId) {
        return repository.findByClientId(clientId);
    }
    public Flux<PaymentHistory> getPaymentsByDni(String dni) {
        return repository.findByDni(dni);
    }


}

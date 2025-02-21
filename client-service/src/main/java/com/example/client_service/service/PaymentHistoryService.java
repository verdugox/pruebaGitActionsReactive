package com.example.client_service.service;

import com.example.client_service.model.PaymentHistory;
import com.example.client_service.repository.PaymentHistoryRepository;
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
    public Flux<PaymentHistory> getAllPayments() {
        return repository.findAll();
    }

    public Flux<PaymentHistory> getPaymentsByClientId(String clientId) {
        return repository.findByClientId(clientId);
    }


}

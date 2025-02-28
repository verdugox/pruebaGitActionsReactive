package com.example.client_service.controller;

import com.example.client_service.model.Client;
import com.example.client_service.model.PaymentHistory;
import com.example.client_service.model.PaymentRequest;
import com.example.client_service.repository.ClientRepository;
import com.example.client_service.repository.PaymentHistoryRepository;
import com.example.client_service.service.ClientService;
import com.example.client_service.service.PaymentHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
public class PaymentHistoryController {
    private final PaymentHistoryService service;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ClientRepository clientRepository;
    private final ClientService clientService;

    public PaymentHistoryController(PaymentHistoryService service,
                                    PaymentHistoryRepository paymentHistoryRepository,
                                    ClientRepository clientRepository,
                                    ClientService clientService) {
        this.service = service;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.clientRepository = clientRepository;
        this.clientService = clientService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<PaymentHistory>> registerPayment(@RequestBody Map<String, Object> request) {
        try {
            log.info("📥 Recibida solicitud de pago con datos: {}", request);

            String clientId = (String) request.get("clientId");
            String dni = (String) request.get("dni");
            String voucherUrl = (String) request.get("voucherUrl");
            double monto;

            Object montoObject = request.get("monto");
            if (montoObject instanceof Integer) {
                monto = ((Integer) montoObject).doubleValue();
            } else if (montoObject instanceof Double) {
                monto = (Double) montoObject;
            } else if (montoObject instanceof String) {
                monto = Double.parseDouble((String) montoObject);
            } else {
                log.error("❌ Error: El campo 'monto' tiene un tipo inesperado: {}", montoObject.getClass());
                return Mono.just(ResponseEntity.badRequest().build());
            }

            log.info("✅ Datos procesados correctamente. Registrando pago...");

            return service.registerPayment(clientId, dni, monto, voucherUrl)
                    .doOnSuccess(payment -> log.info("✅ Pago registrado con éxito: {}", payment))
                    .doOnError(error -> log.error("❌ Error registrando pago: ", error))
                    .map(ResponseEntity::ok)
                    .defaultIfEmpty(ResponseEntity.badRequest().build());

        } catch (Exception e) {
            log.error("🚨 Error en `registerPayment`: ", e);
            return Mono.just(ResponseEntity.status(500).body(null));
        }
    }


    // ✅ Nuevo endpoint para obtener todos los registros de pagos
    @GetMapping("/all")
    public Flux<PaymentHistory> getAllPayments() {
        return service.getAllPayments();
    }


    @GetMapping("/client/{clientId}")
    public Flux<PaymentHistory> getPaymentsByClientId(@PathVariable String clientId) {
        return service.getPaymentsByClientId(clientId);
    }

    @GetMapping("/dni/{dni}")
    public Flux<PaymentHistory> getPaymentByDni(@PathVariable String dni) {
        return service.getPaymentsByDni(dni);
    }

    @PostMapping("/register-payment")
    public Mono<ResponseEntity<PaymentHistory>> registerNewPayment(@RequestBody PaymentRequest paymentRequest) {
        return clientRepository.findById(paymentRequest.getClientId())
                .flatMap(client -> {
                    // Si el estado del cliente es "pendiente" o "inactivo", actualizarlo a "aprobado"
                    if ("pendiente".equals(client.getEstado()) || "inactivo".equals(client.getEstado())) {
                        client.setEstado("aprobado");
                        return clientRepository.save(client)
                                .then(proceedWithPayment(client, paymentRequest));
                    }
                    return proceedWithPayment(client, paymentRequest);
                })
                .defaultIfEmpty(ResponseEntity.<PaymentHistory>notFound().build());
    }

    private Mono<ResponseEntity<PaymentHistory>> proceedWithPayment(Client client, PaymentRequest paymentRequest) {
        if (paymentRequest.getVoucherUrl() == null || paymentRequest.getVoucherUrl().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        PaymentHistory payment = new PaymentHistory();
        payment.setClientId(paymentRequest.getClientId());
        payment.setDni(client.getDni());
        payment.setVoucherUrl(paymentRequest.getVoucherUrl());
        payment.setMonto(paymentRequest.getMonto());
        payment.setEstado("pendiente");
        payment.setFechaPago(ZonedDateTime.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        return paymentHistoryRepository.save(payment)
                .flatMap(savedPayment ->
                        clientService.sendSubscriptionPaymentNotification(client, savedPayment)
                                .then(Mono.just(ResponseEntity.ok(savedPayment)))
                );
    }





    @GetMapping("/approve-payment/{clientId}")
    public Mono<ResponseEntity<PaymentHistory>> approvePayment(@PathVariable String clientId) {
        return paymentHistoryRepository.findLastPendingPayment(clientId)
                .flatMap(payment -> {
                    if ("pagado".equals(payment.getEstado())) {
                        return Mono.just(ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body(payment));
                    }

                    payment.setEstado("pagado");
                    payment.setFechaPago(ZonedDateTime.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

                    return paymentHistoryRepository.save(payment)
                            .flatMap(updatedPayment ->
                                    clientRepository.findById(payment.getClientId())
                                            .flatMap(client -> clientService.sendPaymentApprovalNotification(client)
                                                    .thenReturn(ResponseEntity.ok(updatedPayment))
                                            ));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }









}

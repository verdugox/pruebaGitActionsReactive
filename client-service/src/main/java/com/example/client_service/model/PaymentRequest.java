package com.example.client_service.model;

import lombok.Data;

@Data
public class PaymentRequest {
    private String clientId;
    private double monto;
    private String voucherUrl;
}

package com.example.client_service.model;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Document(collection = "payment_history")
public class PaymentHistory {

    @Id
    private String id;
    private String clientId; // ID del cliente asociado al pago
    private String dni; // DNI del cliente
    @NotBlank(message = "La URL del voucher es obligatoria")
    private String voucherUrl;
    private double monto; // Monto pagado
    private String estado; // pagado, pendiente, inactivo
    private String fechaPago; // Fecha del pago

    public PaymentHistory() {
        this.fechaPago = ZonedDateTime.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

}

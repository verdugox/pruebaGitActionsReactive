package com.example.client_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Document(collection = "clients")
public class Client {

    @Id
    private String id;

    private String codigoSortec;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "\\d{8}", message = "El DNI debe tener exactamente 8 dígitos y solo contener números")
    private String dni;

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 250, message = "Máximo 250 caracteres")
    @Pattern(regexp = "^[a-zA-ZñÑáéíóúÁÉÍÓÚ ]+$", message = "El formato del nombre no es correcto")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 250, message = "Máximo 250 caracteres")
    @Pattern(regexp = "^[a-zA-ZñÑáéíóúÁÉÍÓÚ ]+$", message = "El formato del apellido no es correcto")
    private String apellidos;

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 250, message = "Se permite máximo 250 caracteres")
    private String direccion;

    @NotBlank(message = "El país es obligatorio")
    @Size(max = 50, message = "Máximo 50 caracteres")
    @Pattern(regexp = "^[a-zA-ZñÑáéíóúÁÉÍÓÚ ]+$", message = "Solo caracteres alfabéticos")
    private String pais;

    @NotBlank(message = "La provincia es obligatoria")
    @Size(max = 80, message = "Máximo 80 caracteres")
    @Pattern(regexp = "^[a-zA-ZñÑáéíóúÁÉÍÓÚ ]+$", message = "Solo caracteres alfabéticos")
    private String provincia;

    @NotBlank(message = "El distrito es obligatorio")
    @Size(max = 80, message = "Máximo 80 caracteres")
    @Pattern(regexp = "^[a-zA-ZñÑáéíóúÁÉÍÓÚ ]+$", message = "Solo caracteres alfabéticos")
    private String distrito;

    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 250, message = "Máximo 250 caracteres")
    @Email(message = "Formato de correo inválido")
    private String correo;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener exactamente 9 dígitos y solo contener números")
    private String telefono;

    @NotBlank(message = "La URL del voucher es obligatoria")
    private String voucherUrl; // URL de la imagen del voucher

    @NotBlank(message = "La referencia de pago es obligatoria")
    private String referenciaPago;

    private String estado; // pendiente, aprobado

    // Nuevo campo fechaRegistro
    private String fechaRegistro;

    public Client() {
        this.estado = "pendiente";
        this.fechaRegistro = ZonedDateTime.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    public void generarCodigoSortec(int correlativo) {
        if (this.nombres != null && this.apellidos != null) {
            char inicialNombre = this.nombres.trim().toUpperCase().charAt(0);
            char inicialApellido = this.apellidos.trim().toUpperCase().charAt(0);
            this.codigoSortec = "SORTEC" + inicialNombre + inicialApellido + String.format("%03d", correlativo);
        }
    }
}

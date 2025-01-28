package com.example.client_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.*;

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

    public Client() {
        this.estado = "pendiente";
    }

    public void generarCodigoSortec(int correlativo) {
        if (this.nombres != null && this.apellidos != null) {
            String primerNombre = this.nombres.split(" ")[0].toUpperCase();
            String primerApellido = this.apellidos.split(" ")[0].toUpperCase();
            this.codigoSortec = "SORTEC" + primerNombre + "-" + primerApellido + String.format("%03d", correlativo);
        }
    }
}

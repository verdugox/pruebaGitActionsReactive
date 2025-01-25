package com.example.client_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "clients")
public class Client {

    @Id
    private String id;

    private String dni;
    private String nombres;
    private String apellidos;
    private String direccion;
    private String pais;
    private String provincia;
    private String distrito;
    private String correo;
    private String telefono;
    private String voucherUrl; // URL de la imagen del voucher
    private String referenciaPago;
    private String estado; // pendiente, aprobado

    public Client() {
        this.estado = "pendiente";
    }
}

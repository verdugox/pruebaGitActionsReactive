package com.example.client_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    private String vaucher;

    private String referenciaPago;

}

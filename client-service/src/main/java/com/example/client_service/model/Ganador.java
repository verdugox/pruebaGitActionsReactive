package com.example.client_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;

@Data
@Document(collection = "ganadores")
public class Ganador {

    @Id
    private String id;

    @NotBlank(message = "El título del ganador es obligatorio")
    private String titulo;

    private String descripcion;

    @NotBlank(message = "La URL de la imagen es obligatoria")
    private String imagenUrl;

    @NotBlank(message = "La fecha del ganador es obligatoria")
    private String fechaGanador; // Formato: dd/MM/yyyy

    public Ganador() {
    }
}

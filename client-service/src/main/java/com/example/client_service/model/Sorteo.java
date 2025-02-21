package com.example.client_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;

@Data
@Document(collection = "sorteos")
public class Sorteo {

    @Id
    private String id;

    @NotBlank(message = "El título del sorteo es obligatorio")
    private String titulo;

    @NotBlank(message = "La URL de la imagen es obligatoria")
    private String imagenUrl;

    @NotBlank(message = "La fecha del sorteo es obligatoria")
    private String fechaSorteo; // Formato: dd/MM/yyyy

    private String estado; // "Activo", "Finalizado", "Proximo"

    public Sorteo() {
        this.estado = "próximo"; // Estado por defecto
    }
}


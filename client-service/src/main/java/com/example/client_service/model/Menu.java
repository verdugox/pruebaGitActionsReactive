package com.example.client_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "menus") // Guardado en MongoDB
public class Menu {
    @Id
    private String id;
    private String rol; // PARTICIPANTE o ADMINISTRADOR
    private List<MenuItem> items; // Lista de opciones del menú
}

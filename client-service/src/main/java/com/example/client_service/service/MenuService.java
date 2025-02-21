package com.example.client_service.service;

import com.example.client_service.model.Menu;
import com.example.client_service.repository.MenuRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    /**
     * Obtiene el menú basado en el rol del usuario.
     */
    public Mono<Menu> getMenuByRol(String rol) {
        return menuRepository.findByRol(rol);
    }

    /**
     * Obtiene todos los menús almacenados.
     */
    public Flux<Menu> getAllMenus() {
        return menuRepository.findAll();
    }

    /**
     * Guarda un nuevo menú en la base de datos.
     */
    public Mono<Menu> saveMenu(Menu menu) {
        return menuRepository.save(menu);
    }

    /**
     * Actualiza un menú existente por su rol.
     */
    public Mono<Menu> updateMenu(String rol, Menu menu) {
        return menuRepository.findByRol(rol)
                .flatMap(existingMenu -> {
                    existingMenu.setItems(menu.getItems());
                    return menuRepository.save(existingMenu);
                });
    }

    /**
     * Elimina un menú por su rol.
     */
    public Mono<Void> deleteMenu(String rol) {
        return menuRepository.findByRol(rol)
                .flatMap(menuRepository::delete);
    }
}

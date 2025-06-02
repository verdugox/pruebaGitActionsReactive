package com.example.client_service.controller;

import com.example.client_service.model.Menu;
import com.example.client_service.service.MenuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CrossOrigin(origins = {
        "http://localhost:3000",
        "https://sortsortech.azurewebsites.net"
})
@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * Obtener menú por rol.
     */
    @GetMapping("/{rol}")
    public Mono<ResponseEntity<Menu>> getMenuByRol(@PathVariable String rol) {
        return menuService.getMenuByRol(rol)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Obtener todos los menús.
     */
    @GetMapping
    public Flux<Menu> getAllMenus() {
        return menuService.getAllMenus();
    }

    /**
     * Crear un nuevo menú.
     */
    @PostMapping
    public Mono<ResponseEntity<Menu>> createMenu(@RequestBody Menu menu) {
        return menuService.saveMenu(menu)
                .map(ResponseEntity::ok);
    }

    /**
     * Actualizar un menú por su rol.
     */
    @PutMapping("/{rol}")
    public Mono<ResponseEntity<Menu>> updateMenu(@PathVariable String rol, @RequestBody Menu menu) {
        return menuService.updateMenu(rol, menu)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Eliminar un menú por rol.
     */
    @DeleteMapping("/{rol}")
    public Mono<ResponseEntity<Void>> deleteMenu(@PathVariable String rol) {
        return menuService.deleteMenu(rol)
                .thenReturn(ResponseEntity.noContent().build());
    }
}

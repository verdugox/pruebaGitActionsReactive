package com.example.client_service.service;

import com.example.client_service.model.Client;
import com.example.client_service.repository.ClientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ClientService {

    private final ClientRepository repository;
    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl; // URL dinámica tomada desde application.yml



    private static final String ADMIN_EMAIL = "administrador@sorteosc.com";

    @Value("${app.sorteo-image-url}")
    private String sorteoImageUrl; // URL de la imagen parametrizada desde secrets o configuración


    public ClientService(ClientRepository repository, JavaMailSender mailSender) {
        this.repository = repository;
        this.mailSender = mailSender;
    }

    public Flux<Client> getAllClients() {
        return repository.findAll();
    }

    public Mono<Client> getClientById(String id) {
        return repository.findById(id);
    }

    /**
     * Guarda el cliente y envía notificación al administrador con la URL del voucher.
     */
    public Mono<Client> saveClient(Client client) {
        client.setEstado("pendiente");

        return repository.save(client)
                .flatMap(savedClient ->
                        sendAdminNotification(savedClient)
                                .thenReturn(savedClient)
                );
    }

    /**
     * Elimina el cliente.
     */
    public Mono<ResponseEntity<Void>> deleteClient(String id) {
        return repository.findById(id)
                .flatMap(existingClient ->
                        repository.delete(existingClient)
                                .then(Mono.fromSupplier(() -> ResponseEntity.noContent().<Void>build())) // Conversión segura a ResponseEntity<Void>
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }


    /**
     * Actualiza el cliente.
     */
    public Mono<ResponseEntity<Client>> updateClient(String id, Client client) {
        return repository.findById(id)
                .flatMap(existingClient -> {
                    existingClient.setDni(client.getDni());
                    existingClient.setNombres(client.getNombres());
                    existingClient.setApellidos(client.getApellidos());
                    existingClient.setDireccion(client.getDireccion());
                    existingClient.setPais(client.getPais());
                    existingClient.setProvincia(client.getProvincia());
                    existingClient.setDistrito(client.getDistrito());
                    existingClient.setCorreo(client.getCorreo());
                    existingClient.setTelefono(client.getTelefono());
                    existingClient.setVoucherUrl(client.getVoucherUrl());
                    existingClient.setReferenciaPago(client.getReferenciaPago());
                    existingClient.setEstado(client.getEstado());
                    return repository.save(existingClient);
                })
                .map(updatedClient -> ResponseEntity.ok(updatedClient))
                .defaultIfEmpty(ResponseEntity.notFound().build()); // Manejo de cliente no encontrado
    }


    /**
     * Aprueba el cliente y le envía un correo con la confirmación del sorteo.
     */
    public Mono<ResponseEntity<Client>> approveClient(String id) {
        return repository.findById(id)
                .flatMap(client -> {
                    if ("aprobado".equals(client.getEstado())) {
                        return Mono.just(ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body(client));
                    }
                    client.setEstado("aprobado");
                    return repository.save(client)
                            .flatMap(updatedClient ->
                                    sendApprovalNotification(updatedClient)
                                            .thenReturn(ResponseEntity.ok(updatedClient))
                            );
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Envía un correo al administrador con el voucher adjunto.
     */
    private Mono<Void> sendAdminNotification(Client client) {
        return Mono.fromRunnable(() -> {
            try {
                log.info("📧 Enviando correo de notificación a: {}", ADMIN_EMAIL);

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setTo(ADMIN_EMAIL);
                helper.setSubject("Nuevo Registro Pendiente");

                String approvalLink = baseUrl + "/api/clients/approve/" + client.getId();

                String content = "<p>El cliente <b>" + client.getNombres() + " " + client.getApellidos() + "</b> ha registrado un pago.</p>"
                        + "<p>Por favor, revisa y aprueba la solicitud en el siguiente enlace:</p>"
                        + "<a href='" + approvalLink + "'>Aprobar Cliente</a>"
                        + "<p><b>Imagen del voucher:</b></p>"
                        + "<img src='" + client.getVoucherUrl() + "' width='300'/>";

                helper.setText(content, true);
                mailSender.send(message);

                log.info("✅ Correo enviado correctamente a: {}", ADMIN_EMAIL);

            } catch (MessagingException e) {
                log.error("🚨 Error al enviar el correo de notificación: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Envía un correo al cliente con la confirmación del registro y la imagen del sorteo.
     */
    private Mono<Void> sendApprovalNotification(Client client) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("Registro Aprobado");

                String content = "<p>Hola <b>" + client.getNombres() + "</b>,</p>"
                        + "<p>Tu registro ha sido aprobado. ¡Gracias por participar en nuestro sorteo!</p>"
                        + "<p><b>Detalles del sorteo:</b></p>"
                        + "<img src='" + sorteoImageUrl + "' width='600'/>";

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                log.error("🚨 Error al enviar el correo de aprobación: {}", e.getMessage(), e);
            }
        });
    }
}

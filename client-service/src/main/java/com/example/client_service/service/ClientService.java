package com.example.client_service.service;

import com.example.client_service.model.Client;
import com.example.client_service.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ClientService {

    private final ClientRepository repository;
    private final JavaMailSender mailSender;

    private static final String ADMIN_EMAIL = "administrador@sorteosc.com";
    private static final String SORTEO_IMAGE_URL = "https://res.cloudinary.com/dizkdk1te/image/upload/v1737819950/sorteolaptop_qxfrux.webp";

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
                .doOnSuccess(savedClient -> {
                    try {
                        sendAdminNotification(savedClient);
                    } catch (MessagingException e) {
                        e.printStackTrace();
                    }
                });
    }

    public Mono<Void> deleteClient(String id) {
        return repository.deleteById(id);
    }

    /**
     * Aprueba el cliente y le envía un correo con la confirmación y la información del sorteo.
     */
    public Mono<ResponseEntity<Client>> approveClient(String id) {
        return repository.findById(id)
                .flatMap(client -> {
                    if ("aprobado".equals(client.getEstado())) {
                        return Mono.just(ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body(client));
                    }
                    client.setEstado("aprobado");
                    return repository.save(client)
                            .doOnSuccess(updatedClient -> {
                                try {
                                    sendApprovalNotification(updatedClient);
                                } catch (MessagingException e) {
                                    e.printStackTrace();
                                }
                            })
                            .map(ResponseEntity::ok);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Envía un correo al administrador con el voucher adjunto.
     */
    private void sendAdminNotification(Client client) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(ADMIN_EMAIL);
        helper.setTo(ADMIN_EMAIL);
        helper.setSubject("Nuevo Registro Pendiente");

        String content = "<p>El cliente <b>" + client.getNombres() + " " + client.getApellidos() + "</b> ha registrado un pago.</p>"
                + "<p>Por favor, revisa y aprueba la solicitud en el siguiente enlace:</p>"
                + "<a href='http://48.216.202.189/api/clients/approve/" + client.getId() + "'>Aprobar Cliente</a>"
                + "<p><b>Imagen del voucher:</b></p>"
                + "<img src='" + client.getVoucherUrl() + "' width='300'/>"; // Muestra la imagen en el correo

        helper.setText(content, true);
        mailSender.send(message);
    }

    /**
     * Envía un correo al cliente con la confirmación del registro y la imagen del sorteo.
     */
    private void sendApprovalNotification(Client client) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(ADMIN_EMAIL);
        helper.setTo(client.getCorreo());
        helper.setSubject("Registro Aprobado");

        String content = "<p>Hola <b>" + client.getNombres() + "</b>,</p>"
                + "<p>Tu registro ha sido aprobado. ¡Gracias por participar en nuestro sorteo!</p>"
                + "<p><b>Detalles del sorteo:</b></p>"
                + "<img src='" + SORTEO_IMAGE_URL + "' width='600'/>";

        helper.setText(content, true);
        mailSender.send(message);
    }
}

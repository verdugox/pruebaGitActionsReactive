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

        return repository.count() // Obtener el número de registros existentes
                .map(Long::intValue) // Convertir Long a int
                .map(count -> count + 1) // Incrementar el contador para generar el código único
                .flatMap(correlativo -> {
                    client.generarCodigoSortec(correlativo);
                    return repository.save(client);
                })
                .flatMap(savedClient -> sendAdminNotification(savedClient).thenReturn(savedClient));
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

    public Mono<ResponseEntity<Client>> denyClient(String id) {
        return repository.findById(id)
                .flatMap(client -> {
                    client.setEstado("denegado");
                    return repository.save(client)
                            .flatMap(updatedClient -> sendDeniedNotification(updatedClient)
                                    .thenReturn(ResponseEntity.ok(updatedClient)));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Envía un correo al administrador con los enlaces para aprobar o denegar al cliente.
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
                String denialLink = baseUrl + "/api/clients/deny/" + client.getId();

                String content = "<p>El cliente <b>" + client.getNombres() + " " + client.getApellidos() + "</b> ha registrado un pago.</p>"
                        + "<p>Por favor, revisa la solicitud:</p>"
                        + "<a href='" + approvalLink + "' style='color: green; font-weight: bold;'>Aprobar Cliente</a> | "
                        + "<a href='" + denialLink + "' style='color: red; font-weight: bold;'>Denegar Cliente</a>"
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
                        + "<p><strong>Tu código generado para el sorteo es: <span style='font-size:18px; color:#1D72F3;'>" + client.getCodigoSortec() + "</span></strong></p>"
                        + "<p>Este código se usará para realizar el sorteo y podrás revisarlo en la página web:</p>"
                        + "<p><a href='https://sortsortech.azurewebsites.net/' style='color: #1D72F3; font-weight: bold;'>https://sortsortech.azurewebsites.net/</a></p>"
                        + "<p>En el listado de participantes aprobados para el sorteo.</p>"
                        + "<p><b>Detalles del sorteo:</b></p>"
                        + "<img src='" + sorteoImageUrl + "' width='600'/>"
                        + "<hr>"
                        + "<p style='color:#FF5733; font-weight:bold;'>📢 No olvidar revisar la página web de SORTEC:</p>"
                        + "<p><a href='https://www.facebook.com/people/Sortec/61571509086893/' style='color: #1877F2; font-weight: bold;'>https://www.facebook.com/people/Sortec/61571509086893/</a></p>"
                        + "<p>👍 Dale <b>'Me gusta'</b> o <b>suscríbete</b> para enterarte de nuevos sorteos y del día en que realizaremos este sorteo.</p>";

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                log.error("🚨 Error al enviar el correo de aprobación: {}", e.getMessage(), e);
            }
        });
    }



    private Mono<Void> sendDeniedNotification(Client client) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("Registro Denegado");

                String content = "<p>Hola <b>" + client.getNombres() + "</b>,</p>"
                        + "<p>Tu registro no fue aprobado porque el voucher de pago que adjuntaste es erróneo y nunca se procesó.</p>"
                        + "<p>Por favor, revisa el cobro de tu Yape o banco. Si no se cobraron, vuelve a registrarte en: "
                        + "<a href='https://sortsortech.azurewebsites.net/'>https://sortsortech.azurewebsites.net/</a></p>"
                        + "<p>Si se cobró en tu Yape o banco, contáctanos al 977559149 (Luis Acuña) para más información.</p>"
                        + "<p>Gracias y disculpa las molestias.</p>";

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                log.error("Error al enviar notificación de denegación: {}", e.getMessage(), e);
            }
        });
    }
}

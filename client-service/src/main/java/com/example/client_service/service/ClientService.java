package com.example.client_service.service;

import com.example.client_service.model.Client;
import com.example.client_service.model.PaymentHistory;
import com.example.client_service.repository.ClientRepository;
import com.example.client_service.repository.PaymentHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.azure.messaging.eventhubs.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;


@Slf4j
@Service
public class ClientService {

    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ClientRepository repository;
    private final JavaMailSender mailSender;

    private final EventHubProducerAsyncClient eventHubClient;
    private final ObjectMapper objectMapper;

    @Value("${app.base-url}")
    private String baseUrl; // URL dinámica tomada desde application.yml

    @Value("${whatsapp.token}")
    private String whatsappToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.api-url}")
    private String whatsappApiUrl;

    private static final String ADMIN_EMAIL = "administrador@sorteosc.com";

    @Value("${app.sorteo-image-url}")
    private String sorteoImageUrl; // URL de la imagen parametrizada desde secrets o configuración


    public ClientService(ClientRepository repository, JavaMailSender mailSender,
                         PaymentHistoryRepository paymentHistoryRepository,
                         @Value("${azure.eventhub.connection-string}") String connectionString,
                         @Value("${azure.eventhub.name}") String eventHubName,
                         ObjectMapper objectMapper) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.objectMapper = objectMapper;
        this.eventHubClient = new EventHubClientBuilder()
                .connectionString(connectionString, eventHubName)
                .buildAsyncProducerClient();
    }

    public Flux<Client> getAllClients() {
        return repository.findAll();
    }

    public Mono<Client> getClientById(String id) {
        return repository.findById(id);
    }

    public Mono<Client> getClientByDni(String dni) {
        return repository.findByDni(dni);
    }

    /**
     * Guarda el cliente y envía notificación al administrador con la URL del voucher.
     */
    public Mono<Client> saveClient(Client client) {
        if (client.getVoucherUrl() == null || client.getVoucherUrl().isEmpty()) {
            return Mono.error(new IllegalArgumentException("El voucher de pago es obligatorio."));
        }

        return repository.findByDniOrCorreo(client.getDni(), client.getCorreo())
                .flatMap(existingClient -> {
                    String mensajeError = existingClient.getDni().equals(client.getDni())
                            ? "Ya existe un cliente registrado con este DNI."
                            : "Ya existe un cliente registrado con este correo.";
                    return Mono.error(new IllegalArgumentException(mensajeError));
                })
                .cast(Client.class)
                .switchIfEmpty(Mono.defer(() -> {
                    client.setEstado("pendiente");
                    client.setFechaRegistro(ZonedDateTime.now(ZoneId.of("America/Lima"))
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

                    return repository.count()
                            .map(Long::intValue)
                            .map(count -> count + 1)
                            .flatMap(correlativo -> {
                                client.generarCodigoSortec(correlativo);
                                return repository.save(client);
                            })
                            .flatMap(savedClient -> {
                                PaymentHistory payment = new PaymentHistory();
                                payment.setClientId(savedClient.getId());
                                payment.setDni(savedClient.getDni());
                                payment.setVoucherUrl(savedClient.getVoucherUrl());
                                payment.setMonto(8.0);
                                payment.setEstado("pendiente");
                                payment.setFechaPago(ZonedDateTime.now(ZoneId.of("America/Lima"))
                                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

                                return paymentHistoryRepository.save(payment)
                                        .then(sendAdminNotification(savedClient))
                                        .then(publishClientEvent(savedClient))
                                        .thenReturn(savedClient);
                            });
                }))
                .doOnError(error -> log.error("Error al guardar el cliente: {}", error.getMessage(), error)); // Log de error
    }








    /**
     * Elimina el cliente y su historial de pagos.
     */
    public Mono<ResponseEntity<Void>> deleteClient(String id) {
        return repository.findById(id)
                .flatMap(existingClient ->
                        paymentHistoryRepository.findByClientId(id)
                                .collectList()
                                .flatMap(payments -> {
                                    // Tomar el último pago, si existe
                                    PaymentHistory lastPayment = payments.isEmpty() ? null : payments.get(payments.size() - 1);

                                    return sendDeleteNotification(existingClient, lastPayment)
                                            .thenMany(Flux.fromIterable(payments)
                                                    .flatMap(paymentHistoryRepository::delete))
                                            .then(repository.delete(existingClient))
                                            .then(Mono.fromSupplier(() -> ResponseEntity.noContent().<Void>build()));
                                })
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }


    public Mono<Void> sendDeleteNotification(Client client, PaymentHistory payment) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setCc(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("⚠️ Notificación: Registro Eliminado - SORTEC");

                String content = "<div style='font-family: Arial, sans-serif; color: #b30000; padding: 20px;'>"
                        + "<h1 style='color: #ff0000;'>❌ Registro Eliminado - Advertencia Importante</h1>"
                        + "<p style='font-size: 18px;'>El sistema ha procesado la <b>eliminación de su usuario</b> debido a uno de los siguientes motivos:</p>"
                        + "<ul style='font-size: 17px;'>"
                        + "<li>🔍 Registro automático eliminado por <b>voucher inválido</b> o <b>datos inconsistentes</b> detectados en el sistema.</li>"
                        + "<li>👤 Eliminación realizada de forma <b>manual</b> por el propio usuario desde el módulo correspondiente.</li>"
                        + "</ul>"
                        + "<hr>"
                        + "<h2 style='color: #d9534f;'>📛 Detalles del cliente eliminado:</h2>"
                        + "<ul style='font-size: 16px;'>"
                        + "<li><b>Nombre:</b> " + client.getNombres() + " " + client.getApellidos() + "</li>"
                        + "<li><b>DNI:</b> " + client.getDni() + "</li>"
                        + "<li><b>Correo:</b> " + client.getCorreo() + "</li>"
                        + "<li><b>Fecha de Registro:</b> " + client.getFechaRegistro() + "</li>"
                        + (payment != null ? "<li><b>Monto del intento:</b> S/ " + payment.getMonto() + "</li>" : "")
                        + (payment != null ? "<li><b>Fecha de Pago:</b> " + payment.getFechaPago() + "</li>" : "")
                        + "</ul>"
                        + (payment != null && payment.getVoucherUrl() != null
                        ? "<p><b>📎 Voucher proporcionado:</b></p><img src='" + payment.getVoucherUrl() + "' width='300' style='border: 2px solid red;'/>"
                        : "<p style='color: red;'>⚠️ No se adjuntó un voucher válido.</p>")
                        + "<hr>"
                        + "<p style='font-size: 18px; color: #b30000;'>📢 <b>Acción recomendada:</b> Validar si la eliminación fue justificada propio del usuario, caso contrario volver a registrarse de forma correcta.</p>"
                        + "<p style='font-size: 18px;'>🛠️ Si fue un error humano, por favor volver a realizar el registro con los datos correctos y voucher válido.</p>"
                        + "</div>";

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
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
                    existingClient.setSexo(client.getSexo());
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
                                    paymentHistoryRepository.findByClientId(updatedClient.getId()) // 🔍 Buscar el pago del cliente
                                            .collectList()
                                            .flatMap(payments -> {
                                                if (payments.isEmpty()) {
                                                    log.warn("⚠ No se encontró historial de pagos para el cliente: {}", updatedClient.getId());
                                                    return Mono.just(ResponseEntity.ok(updatedClient));
                                                }

                                                // 🔹 Obtener el último pago (suponiendo que está ordenado por fecha)
                                                PaymentHistory lastPayment = payments.get(payments.size() - 1);
                                                lastPayment.setEstado("pagado"); // ✅ Cambiar estado a "pagado"

                                                return paymentHistoryRepository.save(lastPayment) // 💾 Guardar el estado actualizado
                                                        .doOnSuccess(savedPayment -> log.info("✅ Estado del pago actualizado a 'pagado' para el cliente {}", updatedClient.getId()))
                                                        .then(sendApprovalNotification(updatedClient)) // Enviar correo de aprobación
                                                        .thenReturn(ResponseEntity.ok(updatedClient));
                                            })
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
                helper.setCc(ADMIN_EMAIL);
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
                helper.setCc(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("🎉 ¡Registro Aprobado en SORTEC! 🎟️");

                String content = "<div style='font-family: Arial, sans-serif; text-align: center; color: #333;'>"
                        + "<h1 style='color: #28a745;'>✅ ¡Bienvenido a SORTEC, " + client.getNombres() + "! 🎊</h1>"
                        + "<p style='font-size: 18px;'>Tu registro ha sido <b>aprobado</b> con éxito. 🎟️</p>"
                        + "<h2 style='color: #1D72F3;'>🎯 Tu código generado para el sorteo es:</h2>"
                        + "<p style='font-size: 22px; color:#1D72F3; font-weight: bold;'>" + client.getCodigoSortec() + "</p>"
                        + "<p style='font-size: 18px;'>Este código es único y se usará en el próximo sorteo. 📢 <br>"
                        + "Recuerda guardarlo bien, ya que con este número podrás verificar si eres uno de los ganadores. 🏆</p>"

                        // Explicación detallada sobre cómo iniciar sesión
                        + "<h2 style='color: #28a745;'>🔑 ¿Cómo ingresar a la plataforma SORTEC? 🔑</h2>"
                        + "<p style='font-size: 18px;'>Para acceder y revisar más información sobre el sorteo, sigue estos sencillos pasos:</p>"
                        + "<ol style='font-size: 16px; text-align: left; display: inline-block;'>"
                        + "  <li>🌐 Ingresa a nuestra plataforma: <a href='https://sortsortech.azurewebsites.net/' target='_blank'>🔗 SORTEC</a></li>"
                        + "  <li>🔹 En la parte superior derecha, haz clic en la opción <b>'Iniciar Sesión' 🔑</b></li>"
                        + "  <li>📌 Ingresa tus credenciales:</li>"
                        + "    <ul>"
                        + "      <li>👤 <b>Usuario:</b> " + client.getDni() + " (Tu número de DNI)</li>"
                        + "      <li>🔒 <b>Contraseña:</b> " + client.getCodigoSortec() + " (Tu código Sortec)</li>"
                        + "    </ul>"
                        + "  <li>✅ Presiona el botón <b>'Ingresar'</b> y listo. 🎉</li>"
                        + "</ol>"
                        + "<p style='font-size: 18px;'>📋 Una vez dentro, podrás revisar la sección de <b>'Ver Suscripción'</b>, consultar el estado de tu participación y enterarte de futuros sorteos. 🎁</p>"

                        // Detalles del sorteo
                        + "<h2 style='color: #ff5733;'>📸 Detalles del sorteo</h2>"
                        + "<p>Consulta la información sobre el sorteo en la imagen a continuación:</p>"
                        + "<img src='" + sorteoImageUrl + "' width='600' style='border-radius: 10px; margin-top: 10px;'/>"
                        + "<hr>"

                        // Redes sociales
                        + "<h3 style='color:#FF5733;'>📢 ¡No olvides seguirnos en nuestras redes sociales! 📢</h3>"
                        + "<p style='font-size: 18px;'>🔔 Dale <b>'Me gusta'</b> o <b>suscríbete</b> para estar al tanto de nuevos sorteos y del día en que realizaremos este evento. 🎥</p>"
                        + "<a href='https://www.facebook.com/people/Sortec/61571509086893/' target='_blank' "
                        + "style='display: inline-block; padding: 10px 20px; background-color: #1877F2; color: #fff; text-decoration: none; "
                        + "border-radius: 5px; font-size: 18px;'>👍 SEGUIRNOS EN FACEBOOK</a>"

                        + "<p style='font-size: 18px; margin-top: 20px;'>🙌 <b>Gracias por confiar en SORTEC. ¡Mucha suerte en el sorteo! 🍀🎊</b></p>"
                        + "</div>";

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
                helper.setSubject("Registro Denegado - SORTEC");

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

    public Mono<Void> sendLoginEmail(Client client) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom("administrador@sorteosc.com");
                helper.setTo(client.getCorreo());
                helper.setSubject("Acceso a Sortec");

                String content = "<p>Hola <b>" + client.getNombres() + "</b>,</p>"
                        + "<p>Para acceder a la plataforma, usa tu DNI como usuario y el código de sorteo como contraseña.</p>"
                        + "<p>Accede aquí: <a href='https://sortsortech.azurewebsites.net/login'>Login</a></p>"
                        + "<p>Gracias por participar.</p>";

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                log.error("Error al enviar correo de login: {}", e.getMessage(), e);
            }
        });
    }

    public Mono<Void> sendMassEmail() {
        return repository.findAll()
                .flatMap(client -> sendMassiveEmail(client))
                .then();
    }

    private Mono<Void> sendMassiveEmail(Client client) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("🎉🎊 ¡TRANSMISION EN VIVO - SORTEC - SORTEO 28/02/2025! 🎊🎉");

                String content = "<p style='font-size: 24px; font-weight: bold; text-align: center;'>🎉 ¡Bienvenido a SORTEC! 🎊</p>"
                        + "<p style='font-size: 20px; text-align: center;'>🚀 Tu portal exclusivo de sorteos 🚀</p>"
                        + "<p style='font-size: 18px; text-align: center;'>Nos alegra mucho que formes parte de <b>SORTEC</b> 🎁✨</p>"
                        + "<p style='font-size: 18px; text-align: center;'>¡Prepárate para vivir la emoción de los sorteos en vivo! 🎥🔴</p>"
                        + "<h2 style='text-align: center;'>🎥 ¡MIRA EL STREAMING EN VIVO! 🎥</h2>"
                        + "<p style='font-size: 18px; text-align: center;'>El link del streaming en vivo está disponible en nuestro Facebook:</p>"
                        + "<p style='text-align: center;'>"
                        + "<a href='https://www.facebook.com/1686884439/videos/672127105241426/' target='_blank' style='font-size: 20px; color: blue; font-weight: bold;'>📲 Ir al Streaming en Facebook</a>"
                        + "</p>"
                        + "<h2 style='color: #ff5733;'>📸 Detalles del sorteo</h2>"
                        + "<p>Consulta la información sobre el sorteo en la imagen a continuación:</p>"
                        + "<img src='" + sorteoImageUrl + "' width='600' style='border-radius: 10px; margin-top: 10px;'/>"
                        + "<hr>"
                        + "<h2 style='text-align: center;'>📢 ¡NO TE LO PIERDAS! 📢</h2>"
                        + "<p style='font-size: 18px; text-align: center;'>Sigue nuestras redes para estar al tanto de todas las novedades 🎯🚀</p>"
                        + "<p style='text-align: center;'>"
                        + "<a href='https://www.facebook.com/profile.php?id=61571509086893' target='_blank' style='font-size: 20px; color: blue; font-weight: bold;'>👍 Síguenos en Facebook</a>"
                        + "</p>"
                        + "<h2 style='text-align: center;'>✨ ¡GRACIAS POR FORMAR PARTE DE SORTEC! ✨</h2>"
                        + "<p style='font-size: 18px; text-align: center;'>🎊 ¡Más participantes, más premios! 🎁</p>"
                        + "<p style='font-size: 18px; text-align: center;'>📩 Cualquier duda, contáctanos. ¡Estamos para ayudarte! 🚀</p>"
                        + "<p style='font-size: 18px; text-align: center;'>Atentamente, <b>🎯 El equipo de SORTEC S.A.C. 🚀</b></p>";

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                log.error("Error al enviar correo de bienvenida: {}", e.getMessage(), e);
            }
        });
    }

    /*
     @Scheduled(cron = "0 0 12 * * ?")
    public Mono<Void> checkSubscriptionStatus() {
        return repository.findAll()
                .flatMap(client -> paymentHistoryRepository.findByClientId(client.getId())
                        .collectList()
                        .flatMap(payments -> {
                            if (payments.isEmpty()) {
                                return Mono.empty();
                            }

                            PaymentHistory lastPayment = payments.stream()
                                    .max((p1, p2) -> ZonedDateTime.parse(p1.getFechaPago(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Lima")))
                                            .compareTo(ZonedDateTime.parse(p2.getFechaPago(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Lima"))))
                                    ).orElse(null);

                            if (lastPayment == null || lastPayment.getFechaPago() == null) {
                                return Mono.empty();
                            }

                            ZonedDateTime fechaUltimoPago = ZonedDateTime.parse(lastPayment.getFechaPago(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Lima")));
                            int diaOriginal = fechaUltimoPago.getDayOfMonth();
                            ZonedDateTime fechaVencimiento = fechaUltimoPago.plusMonths(1);

                            // ✅ Ajustar si el mes siguiente no tiene el mismo día
                            if (fechaVencimiento.getDayOfMonth() != diaOriginal) {
                                fechaVencimiento = fechaVencimiento.withDayOfMonth(fechaVencimiento.getMonth().length(fechaVencimiento.toLocalDate().isLeapYear()));
                            }

                            ZonedDateTime fechaNotificacion3Dias = fechaVencimiento.minusDays(3);
                            ZonedDateTime fechaNotificacion2Dias = fechaVencimiento.minusDays(2);
                            ZonedDateTime fechaNotificacion1Dia = fechaVencimiento.minusDays(1);
                            ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of("America/Lima"));

                            if (ahora.isAfter(fechaNotificacion3Dias) && ahora.isBefore(fechaNotificacion2Dias)) {
                                return sendPaymentReminder(client, "Faltan 3 días para que tu suscripción venza.");
                            } else if (ahora.isAfter(fechaNotificacion2Dias) && ahora.isBefore(fechaNotificacion1Dia)) {
                                return sendPaymentReminder(client, "Faltan 2 días para que tu suscripción venza.");
                            } else if (ahora.isAfter(fechaNotificacion1Dia) && ahora.isBefore(fechaVencimiento)) {
                                return sendPaymentReminder(client, "Último día para renovar tu suscripción.");
                            } else if (ahora.isAfter(fechaVencimiento) && ahora.isBefore(fechaVencimiento.plusMonths(2))) {
                                client.setEstado("pendiente");
                                return repository.save(client)
                                        .then(sendSubscriptionExpired(client, "Tu suscripción ha vencido y tu estado es 'pendiente'. Por favor, realiza tu pago."));
                            } else if (ahora.isAfter(fechaVencimiento.plusMonths(2))) {
                                client.setEstado("inactivo");
                                return repository.save(client)
                                        .then(sendSubscriptionExpired(client, "Tu cuenta ha sido inactivada por falta de pago. Para reactivarla, inicia sesión y realiza el pago correspondiente."));
                            }
                            return Mono.empty();
                        })
                ).then();
    }
    */

    @Scheduled(cron = "0 0 12 1,15 * ?") // Se ejecuta el día 1 y 15 de cada mes a las 12:00 pm
    public Mono<Void> checkSubscriptionStatus() {
        return repository.findAll()
                .flatMap(client -> paymentHistoryRepository.findByClientId(client.getId())
                        .collectList()
                        .flatMap(payments -> {
                            if (payments.isEmpty()) {
                                return Mono.empty();
                            }

                            PaymentHistory lastPayment = payments.stream()
                                    .max((p1, p2) -> ZonedDateTime.parse(p1.getFechaPago(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Lima")))
                                            .compareTo(ZonedDateTime.parse(p2.getFechaPago(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Lima"))))
                                    ).orElse(null);

                            if (lastPayment == null || lastPayment.getFechaPago() == null) {
                                return Mono.empty();
                            }

                            ZonedDateTime fechaUltimoPago = ZonedDateTime.parse(lastPayment.getFechaPago(), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Lima")));
                            int diaOriginal = fechaUltimoPago.getDayOfMonth();
                            ZonedDateTime fechaVencimiento = fechaUltimoPago.plusMonths(1);

                            if (fechaVencimiento.getDayOfMonth() != diaOriginal) {
                                fechaVencimiento = fechaVencimiento.withDayOfMonth(fechaVencimiento.getMonth().length(fechaVencimiento.toLocalDate().isLeapYear()));
                            }

                            ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of("America/Lima"));

                            if (ahora.isAfter(fechaVencimiento) && ahora.isBefore(fechaVencimiento.plusMonths(2))) {
                                if (!"pendiente".equalsIgnoreCase(client.getEstado())) {
                                    client.setEstado("pendiente");
                                    return repository.save(client)
                                            .then(sendPaymentReminder(client, "Tu suscripción ha vencido. Por favor, realiza el pago para seguir participando."));
                                }
                                return sendPaymentReminder(client, "Recordatorio: Tu suscripción sigue vencida. Renueva ahora y no pierdas tus beneficios.");
                            } else if (ahora.isAfter(fechaVencimiento.plusMonths(2))) {
                                client.setEstado("inactivo");
                                return repository.save(client)
                                        .then(sendSubscriptionExpired(client, "Tu cuenta ha sido inactivada por falta de pago. Para reactivarla, inicia sesión y realiza el pago correspondiente."));
                            }

                            return Mono.empty();
                        })
                ).then();
    }



    private Mono<Void> sendPaymentReminder(Client client, String message) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage mailMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mailMessage, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("📢 ¡Renueva tu Suscripción en SORTEC! 🏆");

                String content = "<div style='font-family: Arial, sans-serif; text-align: center; color: #333;'>"
                        + "<h1 style='color: #d9534f;'>⚠️ ¡Atención, " + client.getNombres() + " " + client.getApellidos() + "! ⚠️</h1>"
                        + "<p style='font-size: 18px;'>🕒 " + message + "</p>"
                        + "<h2 style='color: #28a745;'>🔄 ¿Cómo renovar tu suscripción? 🔄</h2>"
                        + "<p style='font-size: 18px;'>Sigue estos simples pasos para continuar disfrutando de SORTEC y no perderte los increíbles premios 🎁:</p>"
                        + "<ol style='font-size: 16px; text-align: left; display: inline-block;'>"
                        + "  <li>🔹 Ingresa a nuestra plataforma: <a href='http://sortsortech.azurewebsites.net/' target='_blank'>🔗 SORTEC</a></li>"
                        + "  <li>🔹 Inicia sesión con tus credenciales:</li>"
                        + "    <ul>"
                        + "      <li>📌 <b>Usuario (DNI):</b> " + client.getDni() + "</li>"
                        + "      <li>🔒 <b>Contraseña (Código Sortec):</b> " + client.getCodigoSortec() + "</li>"
                        + "    </ul>"
                        + "  <li>🔹 Dirígete al apartado <b>'Ver Suscripción' 📋</b></li>"
                        + "  <li>🔹 Haz clic en el botón <b>'Renovar Suscripción' 🔄</b></li>"
                        + "  <li>💳 Realiza el pago y ¡listo! 🎉</li>"
                        + "</ol>"
                        + "<p style='font-size: 18px;'>🏆 ¡Continúa participando y prepárate para los próximos premios increíbles! 🎁</p>"
                        + "<img src='" + sorteoImageUrl + "' alt='Sorteo' style='width:100%; max-width:600px; border-radius: 10px; margin-top: 10px;'/>"
                        + "<h3 style='color: #ff5733;'>✨ ¡No dejes pasar esta oportunidad! ✨</h3>"
                        + "<p style='font-size: 18px;'>🔔 Si ya realizaste tu pago, puedes ignorar este mensaje. De lo contrario, <b>asegúrate de renovarlo cuanto antes</b> para seguir participando en los sorteos. 🎟️</p>"
                        + "<a href='http://sortsortech.azurewebsites.net/' target='_blank' "
                        + "style='display: inline-block; padding: 10px 20px; background-color: #007bff; color: #fff; text-decoration: none; "
                        + "border-radius: 5px; font-size: 18px;'>🔄 RENOVAR AHORA</a>"
                        + "<p style='font-size: 18px; margin-top: 20px;'>🙌 <b>Gracias por formar parte de la familia SORTEC. ¡Sigue participando y mucha suerte! 🍀🎊</b></p>"
                        + "</div>";

                helper.setText(content, true);
                mailSender.send(mailMessage);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
    }


    private Mono<Void> sendSubscriptionExpired(Client client, String message) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage mailMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mailMessage, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("📢 ¡Tu Suscripción ha Expirado! - SORTEC ⏳");

                String content = "<div style='font-family: Arial, sans-serif; text-align: center; color: #333;'>"
                        + "<h1 style='color: #d9534f;'>⏳ ¡Tu suscripción ha expirado, " + client.getNombres() + " " + client.getApellidos() + "! ⏳</h1>"
                        + "<p style='font-size: 18px;'>⚠️ " + message + "</p>"
                        + "<h2 style='color: #28a745;'>🔄 ¿Cómo reactivar tu suscripción? 🔄</h2>"
                        + "<p style='font-size: 18px;'>No te preocupes, ¡es fácil! Sigue estos pasos para volver a disfrutar de los beneficios de SORTEC 🎉:</p>"
                        + "<ol style='font-size: 16px; text-align: left; display: inline-block;'>"
                        + "  <li>🔹 Ingresa a nuestra plataforma: <a href='http://sortsortech.azurewebsites.net/' target='_blank'>🔗 SORTEC</a></li>"
                        + "  <li>🔹 Inicia sesión con tus credenciales:</li>"
                        + "    <ul>"
                        + "      <li>📌 <b>Usuario (DNI):</b> " + client.getDni() + "</li>"
                        + "      <li>🔒 <b>Contraseña (Código Sortec):</b> " + client.getCodigoSortec() + "</li>"
                        + "    </ul>"
                        + "  <li>🔹 Ve a la sección <b>'Ver Suscripción' 📋</b></li>"
                        + "  <li>🔹 Presiona el botón <b>'Reactivar Suscripción' 🔄</b></li>"
                        + "  <li>💳 Realiza tu pago y ¡listo! 🎉</li>"
                        + "</ol>"
                        + "<p style='font-size: 18px;'>🏆 ¡Continúa participando y prepárate para los próximos premios increíbles! 🎁</p>"
                        + "<img src='" + sorteoImageUrl + "' alt='Sorteo' style='width:100%; max-width:600px; border-radius: 10px; margin-top: 10px;'/>"
                        + "<h3 style='color: #ff5733;'>✨ ¡Te extrañamos en la familia SORTEC! ✨</h3>"
                        + "<p style='font-size: 18px;'>🚀 No dejes pasar la oportunidad de seguir participando en nuestros sorteos y ganar increíbles premios. 🎁</p>"
                        + "<a href='http://sortsortech.azurewebsites.net/' target='_blank' "
                        + "style='display: inline-block; padding: 10px 20px; background-color: #007bff; color: #fff; text-decoration: none; "
                        + "border-radius: 5px; font-size: 18px;'>🔄 REACTIVAR AHORA</a>"
                        + "<p style='font-size: 18px; margin-top: 20px;'>🙌 <b>¡Gracias por formar parte de SORTEC! Esperamos verte de vuelta pronto. 🍀🎊</b></p>"
                        + "</div>";

                helper.setText(content, true);
                mailSender.send(mailMessage);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
    }



    public Mono<Void> sendSubscriptionPaymentNotification(Client client, PaymentHistory payment) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setCc(ADMIN_EMAIL);
                helper.setTo(ADMIN_EMAIL);
                helper.setSubject("Nuevo Pago de Suscripción Pendiente - SORTEC");

                String approvalLink = baseUrl + "/api/payments/approve-payment/" + client.getId();

                String content = "<p>El cliente <b>" + client.getNombres() + " " + client.getApellidos() + "</b> ha registrado un pago de renovación.</p>"
                        + "<p>Por favor, revisa la solicitud y aprueba si es correcto:</p>"
                        + "<a href='" + approvalLink + "' style='color: green; font-weight: bold;'>Aprobar Pago</a>"
                        + "<p><b>Monto:</b> S/ " + payment.getMonto() + "</p>"
                        + "<p><b>Fecha de Pago:</b> " + payment.getFechaPago() + "</p>"
                        + "<p><b>Imagen del Voucher:</b></p>"
                        + "<img src='" + payment.getVoucherUrl() + "' width='300'/>"; // ✅ Enviamos el voucher del payment

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
    }


    public Mono<Void> sendPaymentApprovalNotification(Client client) { // Se agrega parámetro para la imagen del sorteo
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setCc(ADMIN_EMAIL);
                helper.setTo(client.getCorreo());
                helper.setSubject("🎉 ¡Tu Pago de Suscripción ha sido Aprobado! - SORTEC 🚀");

                String content = "<div style='font-family: Arial, sans-serif; text-align: center; color: #333;'>"
                        + "<h1 style='color: #28a745;'>✅ ¡Felicidades, " + client.getNombres() + "! 🎊</h1>"
                        + "<p style='font-size: 18px;'>💳 Tu pago de suscripción ha sido <b>aprobado con éxito</b>. Gracias por seguir siendo parte de <b>la familia SORTEC</b> 💙.</p>"
                        + "<p style='font-size: 18px;'>🏆 ¡Continúa participando y prepárate para los próximos premios increíbles! 🎁</p>"
                        + "<img src='" + sorteoImageUrl + "' alt='Sorteo' style='width:100%; max-width:600px; border-radius: 10px; margin-top: 10px;'/>"
                        + "<h2 style='color: #ff5733;'>📢 ¡No olvides seguirnos en Facebook! 📢</h2>"
                        + "<p style='font-size: 18px;'>Dale like 👍, comparte con tus amigos y mantente informado de todos nuestros sorteos.</p>"
                        + "<a href='https://www.facebook.com/profile.php?id=61571509086893' target='_blank' "
                        + "style='display: inline-block; padding: 10px 20px; background-color: #1877f2; color: #fff; text-decoration: none; "
                        + "border-radius: 5px; font-size: 18px;'>📲 SEGUIR EN FACEBOOK</a>"
                        + "<p style='font-size: 18px; margin-top: 20px;'>💖 <b>Gracias por confiar en SORTEC. ¡Te deseamos mucha suerte! 🍀🎉</b></p>"
                        + "</div>";

                helper.setText(content, true);
                mailSender.send(message);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
    }


    public Mono<Void> sendDynamicMassEmail(String subject, String message, List<String> imageUrls) {
        return repository.findAll()
                .flatMap(client -> sendEmail(client.getCorreo(), subject, message, imageUrls))
                .then();
    }

    public Mono<Void> sendWinnerNotification(String codigoSortec, String subject, String message, List<String> imageUrls) {
        return repository.findByCodigoSortecIgnoreCase(codigoSortec.trim()) // ✅ Remueve espacios en blanco
                .doOnNext(client -> log.info("Cliente encontrado: {}", client.getCorreo()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("No se encontró un cliente con el código Sortec: {}", codigoSortec);
                    return Mono.error(new RuntimeException("No se encontró un cliente con el código Sortec: " + codigoSortec));
                }))
                .flatMap(client -> sendEmail(client.getCorreo(), subject, message, imageUrls))
                .then();
    }


    private Mono<Void> sendEmail(String recipientEmail, String subject, String message, List<String> imageUrls) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
                helper.setFrom(ADMIN_EMAIL);
                helper.setTo(recipientEmail);
                helper.setSubject(subject);

                StringBuilder content = new StringBuilder();
                content.append("<p style='font-size: 16px;'>" + message + "</p>");

                if (imageUrls != null && !imageUrls.isEmpty()) {
                    for (String imageUrl : imageUrls) {
                        content.append("<p style='text-align: center;'><img src='" + imageUrl + "' width='600'/></p>");
                    }
                }

                helper.setText(content.toString(), true);
                mailSender.send(mimeMessage);
            } catch (MessagingException e) {
                log.error("Error al enviar correo: {}", e.getMessage(), e);
            }
        });
    }

    public Mono<Void> sendManualSubscriptionReminder(String subject, String message, List<String> imageUrls) {
        return repository.findAll()
                .flatMap(client -> paymentHistoryRepository.findByClientId(client.getId())
                        .collectList()
                        .flatMap(payments -> {
                            if (payments.isEmpty()) {
                                return Mono.empty();
                            }

                            PaymentHistory lastPayment = payments.stream()
                                    .max((p1, p2) -> ZonedDateTime.parse(p1.getFechaPago(),
                                                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                                                            .withZone(ZoneId.of("America/Lima")))
                                            .compareTo(ZonedDateTime.parse(p2.getFechaPago(),
                                                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                                                            .withZone(ZoneId.of("America/Lima")))))
                                    .orElse(null);

                            if (lastPayment == null || lastPayment.getFechaPago() == null) {
                                return Mono.empty();
                            }

                            ZonedDateTime fechaUltimoPago = ZonedDateTime.parse(lastPayment.getFechaPago(),
                                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                                            .withZone(ZoneId.of("America/Lima")));
                            int diaOriginal = fechaUltimoPago.getDayOfMonth();
                            ZonedDateTime fechaVencimiento = fechaUltimoPago.plusMonths(1);

                            // ✅ Ajustar si el mes siguiente no tiene el mismo día
                            if (fechaVencimiento.getDayOfMonth() != diaOriginal) {
                                fechaVencimiento = fechaVencimiento.withDayOfMonth(fechaVencimiento.getMonth().length(fechaVencimiento.toLocalDate().isLeapYear()));
                            }

                            ZonedDateTime fechaNotificacion3Dias = fechaVencimiento.minusDays(3);
                            ZonedDateTime fechaNotificacion2Dias = fechaVencimiento.minusDays(2);
                            ZonedDateTime fechaNotificacion1Dia = fechaVencimiento.minusDays(1);
                            ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of("America/Lima"));
                            ZonedDateTime fechaHaceUnMes = ahora.minusMonths(1);

                            // ✅ Enviar recordatorio a clientes que están por vencer su suscripción
                            if ((ahora.isAfter(fechaNotificacion3Dias) && ahora.isBefore(fechaNotificacion2Dias)) ||
                                    (ahora.isAfter(fechaNotificacion2Dias) && ahora.isBefore(fechaNotificacion1Dia)) ||
                                    (ahora.isAfter(fechaNotificacion1Dia) && ahora.isBefore(fechaVencimiento))) {
                                return sendEmail(client.getCorreo(), subject, message, imageUrls);
                            }

                            // ✅ Enviar recordatorio a clientes cuya suscripción ya venció hace más de 1 mes
                            if (fechaUltimoPago.isBefore(fechaHaceUnMes)) {
                                String emailSubject = "📢 ¡Tu suscripción ha vencido! 🔔 - SORTEC";
                                String emailMessage = "<p style='font-size: 18px;'>👋 Hola <b>" + client.getNombres() + "</b>,</p>"
                                        + "<p>🚨 Hemos notado que tu suscripción ha vencido hace más de un mes. ¡Queremos que sigas formando parte de nuestra comunidad! 🎉</p>"
                                        + "<p>🔑 Para renovarla, sigue estos sencillos pasos:</p>"
                                        + "<ol style='font-size: 16px;'>"
                                        + "  <li>🔹 Ingresa a nuestra plataforma: <a href='http://sortsortech.azurewebsites.net/' target='_blank'>🔗 SORTEC</a></li>"
                                        + "  <li>🔹 Inicia sesión con tus credenciales:</li>"
                                        + "    <ul>"
                                        + "      <li>📌 <b>Usuario:</b> Tu número de DNI</li>"
                                        + "      <li>🔒 <b>Contraseña:</b> Tu código Sortec</li>"
                                        + "    </ul>"
                                        + "  <li>🔹 Dirígete al apartado <b>'Ver Suscripción'</b> 📋</li>"
                                        + "  <li>🔹 Haz clic en el botón <b>'Renovar Suscripción' 🔄</b></li>"
                                        + "</ol>"
                                        + "<p style='font-size: 18px;'>📢 ¡Es rápido y fácil! No dejes pasar la oportunidad de seguir participando en nuestros sorteos. 🎁</p>"
                                        + "<p style='font-size: 20px; font-weight: bold; color: #ff5733;'>✨🎊 ¡MUCHOS MÁS PREMIOS TE ESTÁN ESPERANDO! 🎊✨</p>"
                                        + "<p style='font-size: 18px;'>🙌 <b>Por favor, no dejes de formar parte de la familia SORTEC.</b> Se vienen muchos más premios y necesitamos de tu apoyo. ❤️</p>"
                                        + "<p style='font-size: 18px;'>🙏 ¡Muchas gracias por ser parte de esta increíble comunidad! 🚀</p>";

                                return sendEmail(client.getCorreo(), emailSubject, emailMessage, imageUrls);
                            }

                            return Mono.empty();
                        })
                ).then();
    }

    public Mono<Void> sendMassWhatsAppMessage(String message) {
        return repository.findAll()
                .filter(client -> "pendiente".equalsIgnoreCase(client.getEstado()) || "inactivo".equalsIgnoreCase(client.getEstado()))
                .flatMap(client -> sendWhatsAppMessage(client.getTelefono(), message, client.getNombres()))
                .then();
    }

    public Mono<Void> sendWhatsAppMessage(String phoneNumber, String message, String clientName) {
        return Mono.fromRunnable(() -> {
            try {
                if (phoneNumber == null || phoneNumber.isEmpty()) {
                    System.err.println("🚨 Error: Número de teléfono vacío o nulo.");
                    return;
                }

                String formattedNumber = "51" + phoneNumber; // Código de país Perú (+51)

                String completeMessage = """
                📢 ¡Tu suscripción ha vencido! 🔔 - SORTEC\n
                👋 Hola %s,\n
                🚨 Tu suscripción ha vencido hace más de un mes. Queremos que sigas formando parte de nuestra comunidad. 🎉\n
                🔑 Para renovarla, sigue estos pasos:\n
                1️⃣ Ingresa a nuestra plataforma: 🔗 https://sortsortech.azurewebsites.net/\n
                2️⃣ Inicia sesión con tus credenciales:\n
                   📌 Usuario: Tu número de DNI\n
                   🔒 Contraseña: Tu código Sortec\n
                3️⃣ Dirígete al apartado 'Ver Suscripción' 📋 y haz clic en 'Renovar Suscripción' 🔄\n
                📢 ¡Es rápido y fácil! No dejes pasar la oportunidad de seguir participando en nuestros sorteos. 🎁\n
                ✨ ¡MUCHOS MÁS PREMIOS TE ESTÁN ESPERANDO! 🎊✨\n
                🙏 ¡Gracias por ser parte de esta increíble comunidad! 🚀
                """.formatted(clientName);

                String requestBody = """
                {
                    "messaging_product": "whatsapp",
                    "recipient_type": "individual",
                    "to": "%s",
                    "type": "template",
                    "template": {
                        "name": "hello_world",
                        "language": { "code": "en_US" }
                    }
                }
                """.formatted(formattedNumber);


                System.out.println("🔹 Token usado: " + whatsappToken);
                System.out.println("🔹 Phone Number ID: " + phoneNumberId);
                System.out.println("🔹 API URL: " + whatsappApiUrl);
                System.out.println("🔹 Número destinatario: " + formattedNumber);
                System.out.println("🔹 JSON enviado: " + requestBody);


                // Crear la conexión HTTP
                URL url = new URL(whatsappApiUrl + "/" + phoneNumberId + "/messages");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + whatsappToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Enviar el JSON en la petición HTTP
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Obtener respuesta del servidor
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    System.out.println("✅ Mensaje enviado a " + phoneNumber);
                } else {
                    System.err.println("🚨 Error enviando mensaje. Código: " + responseCode);
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error al enviar mensaje de WhatsApp: " + e.getMessage());
            }
        });
    }

    public Mono<Void> publishClientEvent(Client client) {
        return Mono.fromCallable(() -> {
            Map<String, Object> payload = Map.of(
                    "clienteId", client.getId(),
                    "voucherUrl", client.getVoucherUrl(),
                    "tipo", "registro"
            );
            return objectMapper.writeValueAsString(payload);
        }).flatMap(eventJson -> {
            EventData eventData = new EventData(eventJson);
            return Mono.fromFuture(eventHubClient.send(Collections.singletonList(eventData)).toFuture())
                    .doOnSuccess(unused -> log.info("Evento cliente nuevo enviado correctamente"))
                    .doOnError(error -> log.error("Error al enviar evento", error));
        }).then();
    }




}

package com.shiptracker.service;

import com.shiptracker.event.ShipStatusChangedEvent;
import com.shiptracker.model.Favorite;
import com.shiptracker.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipAlertService {

    private static final Logger log = LoggerFactory.getLogger(ShipAlertService.class);

    private final FavoriteRepository favoriteRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @EventListener
    @Async
    @Transactional(readOnly = true)
    public void onStatusChanged(ShipStatusChangedEvent event) {
        boolean isDeparture = "Atracado".equals(event.getPreviousStatus())
                           && "En navegación".equals(event.getNewStatus());
        boolean isArrival   = "En navegación".equals(event.getPreviousStatus())
                           && "Atracado".equals(event.getNewStatus());

        if (!isDeparture && !isArrival) return;

        List<Favorite> recipients = isDeparture
                ? favoriteRepository.findDepartureAlertsByMmsi(event.getMmsi())
                : favoriteRepository.findArrivalAlertsByMmsi(event.getMmsi());

        if (recipients.isEmpty()) return;

        log.info("Enviando {} alertas de {} para barco {} ({}→{})",
                recipients.size(),
                isDeparture ? "salida" : "llegada",
                event.getShipName(), event.getPreviousStatus(), event.getNewStatus());

        for (Favorite fav : recipients) {
            String userEmail = fav.getUser().getEmail();
            try {
                if (isDeparture) sendDepartureMail(userEmail, event);
                else             sendArrivalMail(userEmail, event);
            } catch (Exception e) {
                log.error("Error enviando alerta a {}: {}", userEmail, e.getMessage());
            }
        }
    }

    // ── Emails ───────────────────────────────────────────────────

    private void sendDepartureMail(String to, ShipStatusChangedEvent e) throws Exception {
        String destText = (e.getDestination() != null && !e.getDestination().isBlank())
                ? " con destino a <strong>" + e.getDestination() + "</strong>"
                : "";
        String html = buildHtml(
                "&#128674; " + e.getShipName() + " ha zarpado",
                "Tu barco favorito <strong>" + e.getShipName() + "</strong> ha salido de puerto" + destText + ".",
                e.getMmsi(),
                "#22c55e",
                "Ver barco en tiempo real"
        );
        send(to, "ShipTracker — " + e.getShipName() + " ha zarpado", html);
    }

    private void sendArrivalMail(String to, ShipStatusChangedEvent e) throws Exception {
        String html = buildHtml(
                "&#9875; " + e.getShipName() + " ha atracado",
                "Tu barco favorito <strong>" + e.getShipName() + "</strong> ha llegado a puerto y está atracado.",
                e.getMmsi(),
                "#3b82f6",
                "Ver barco en el mapa"
        );
        send(to, "ShipTracker — " + e.getShipName() + " ha atracado", html);
    }

    private void send(String to, String subject, String htmlBody) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(msg);
        log.info("Alerta enviada a {}: {}", to, subject);
    }

    private String buildHtml(String title, String body, String mmsi, String accentColor, String btnText) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#0f172a;font-family:'Segoe UI',Arial,sans-serif;color:#f1f5f9;">
                  <div style="max-width:560px;margin:40px auto;background:#1e293b;border-radius:12px;overflow:hidden;border:1px solid #334155;">
                    <div style="background:linear-gradient(135deg,#0c2340,#1e3a5f);padding:28px 32px;">
                      <div style="font-size:28px;margin-bottom:8px;">&#9875; ShipTracker</div>
                      <h1 style="margin:0;font-size:20px;font-weight:600;color:#f1f5f9;">%s</h1>
                    </div>
                    <div style="padding:28px 32px;">
                      <p style="font-size:15px;line-height:1.6;color:#cbd5e1;margin:0 0 24px;">%s</p>
                      <a href="http://localhost:8080/ships/%s"
                         style="display:inline-block;padding:12px 24px;background:%s;color:#fff;border-radius:8px;text-decoration:none;font-weight:600;font-size:14px;">
                        %s
                      </a>
                    </div>
                    <div style="padding:16px 32px;border-top:1px solid #334155;font-size:12px;color:#64748b;">
                      ShipTracker — rastreador de barcos en tiempo real &#183; Puedes gestionar tus alertas en <a href="http://localhost:8080/favorites" style="color:#60a5fa;">Mis Favoritos</a>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(title, body, mmsi, accentColor, btnText);
    }
}

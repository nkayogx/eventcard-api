package com.kayogx.eventcard.controller;

import com.kayogx.eventcard.exception.NotAllowedException;
import com.kayogx.eventcard.repository.MessageRepository;
import com.kayogx.eventcard.service.AllCompaniesTransaction;
import com.kayogx.eventcard.service.MessageOutcomes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * PUBLIC addresses where WhatsApp (Meta) and Beem tell us what happened to our messages
 * ("delivered", "read", "failed"...). They are checked so only the real services can use them:
 *   - Meta signs every report with our app secret (header X-Hub-Signature-256);
 *   - Beem's reports must carry our secret token in the address.
 */
@RestController
@RequestMapping("/api/public/webhooks")
@Slf4j
public class DeliveryReportsController {

    private final MessageRepository messageRepository;
    private final MessageOutcomes outcomes;
    private final AllCompaniesTransaction allCompaniesTransaction;
    private final JsonMapper jsonMapper;
    private final String whatsAppAppSecret;
    private final String whatsAppVerifyToken;
    private final String beemToken;

    public DeliveryReportsController(MessageRepository messageRepository,
                                     MessageOutcomes outcomes,
                                     AllCompaniesTransaction allCompaniesTransaction,
                                     JsonMapper jsonMapper,
                                     @Value("${app.messaging.meta.app-secret}") String whatsAppAppSecret,
                                     @Value("${app.messaging.meta.verify-token}") String whatsAppVerifyToken,
                                     @Value("${app.messaging.beem.webhook-token}") String beemToken) {
        this.messageRepository = messageRepository;
        this.outcomes = outcomes;
        this.allCompaniesTransaction = allCompaniesTransaction;
        this.jsonMapper = jsonMapper;
        this.whatsAppAppSecret = whatsAppAppSecret;
        this.whatsAppVerifyToken = whatsAppVerifyToken;
        this.beemToken = beemToken;
    }

    // ---------- WhatsApp (Meta) ----------

    /** Meta calls this once when the webhook is set up, to check we are the real owner. */
    @GetMapping("/whatsapp")
    public String confirmWhatsAppWebhook(@RequestParam("hub.mode") String mode,
                                         @RequestParam("hub.verify_token") String verifyToken,
                                         @RequestParam("hub.challenge") String challenge) {
        if (!"subscribe".equals(mode) || !whatsAppVerifyToken.equals(verifyToken)) {
            throw new NotAllowedException("Wrong verify token");
        }
        return challenge;
    }

    @PostMapping("/whatsapp")
    public void whatsAppReport(@RequestBody String body,
                               @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!isSignedByMeta(body, signature)) {
            throw new NotAllowedException("The report is not signed by WhatsApp");
        }
        // A report can hold many status updates: entry[] -> changes[] -> value.statuses[]
        for (JsonNode entry : jsonMapper.readTree(body).path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode status : change.path("value").path("statuses")) {
                    String reason = status.path("errors").path(0).path("title").asText("WhatsApp could not deliver the message");
                    applyReport(status.path("id").asText(), status.path("status").asText(), reason);
                }
            }
        }
    }

    private boolean isSignedByMeta(String body, String signature) {
        if (signature == null || whatsAppAppSecret.isBlank()) {
            return false;
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(whatsAppAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(hmac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            // Compare in a way that takes the same time whatever the input (no hints for attackers)
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception problem) {
            return false;
        }
    }

    // ---------- SMS (Beem) ----------

    /** Beem's delivery report: { "request_id": ..., "status": "DELIVERED" | "UNDELIVERED" | ... } (one or a list). */
    @PostMapping("/beem")
    public void beemReport(@RequestParam String token, @RequestBody String body) {
        if (!MessageDigest.isEqual(beemToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new NotAllowedException("Wrong token");
        }
        JsonNode report = jsonMapper.readTree(body);
        Iterable<JsonNode> reports = report.isArray() ? report : List.of(report);
        for (JsonNode single : reports) {
            String status = single.path("status").asText().toUpperCase();
            String ourStatus = switch (status) {
                case "DELIVERED" -> "delivered";
                case "UNDELIVERED", "FAILED", "REJECTED", "EXPIRED" -> "failed";
                default -> "";
            };
            applyReport(single.path("request_id").asText(), ourStatus, "The SMS could not be delivered (" + status + ")");
        }
    }

    // ---------- shared ----------

    /** Finds our message by the service's id and records the new status. Unknown ids are ignored. */
    private void applyReport(String providerMessageId, String status, String failureReason) {
        if (providerMessageId.isBlank() || status.isBlank()) {
            return;
        }
        allCompaniesTransaction.run(() -> {
            messageRepository.findFirstByProviderMessageId(providerMessageId).ifPresent(message -> {
                switch (status) {
                    case "delivered" -> outcomes.markDelivered(message);
                    case "read" -> outcomes.markRead(message);
                    case "failed" -> outcomes.markFailed(message, failureReason);
                    default -> { /* "sent" and others: nothing new */ }
                }
            });
            return null;
        });
    }
}

package com.kayogx.eventcard.messaging.senders;

import com.kayogx.eventcard.billing.MessageChannel;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Sends cards through Meta's WhatsApp Cloud API, using an APPROVED message template:
 * the guest's card picture as the header, and name, company, event, date and link in the text.
 *
 * Meta documentation: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-message-templates
 */
public class MetaWhatsAppSender implements MessageSender {

    /** Meta's error code for "this number is not on WhatsApp / cannot receive the message". */
    private static final int NOT_DELIVERABLE = 131026;

    private final RestClient meta;
    private final String phoneNumberId;
    private final JsonMapper jsonMapper;

    public MetaWhatsAppSender(String apiVersion, String phoneNumberId, String accessToken, JsonMapper jsonMapper) {
        this.meta = RestClient.builder()
                .baseUrl("https://graph.facebook.com/" + apiVersion)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
        this.phoneNumberId = phoneNumberId;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public MessageChannel channel() {
        return MessageChannel.WHATSAPP;
    }

    @Override
    public SendResult send(OutgoingMessage message) throws SendFailure {
        List<Map<String, Object>> textValues = message.templateValues().stream()
                .map(value -> Map.<String, Object>of("type", "text", "text", value))
                .toList();
        Map<String, Object> request = Map.of(
                "messaging_product", "whatsapp",
                "to", message.toPhone().replace("+", ""),
                "type", "template",
                "template", Map.of(
                        "name", message.templateName(),
                        "language", Map.of("code", message.languageCode()),
                        "components", List.of(
                                Map.of("type", "header", "parameters",
                                        List.of(Map.of("type", "image", "image", Map.of("link", message.cardImageUrl())))),
                                Map.of("type", "body", "parameters", textValues))));
        try {
            String answer = meta.post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            String messageId = jsonMapper.readTree(answer).path("messages").path(0).path("id").asText();
            return new SendResult(messageId, false);
        } catch (RestClientResponseException refused) {
            throw failureFrom(refused.getStatusCode(), refused.getResponseBodyAsString());
        } catch (RestClientException networkProblem) {
            throw new SendFailure("Could not reach WhatsApp, trying again later", true);
        }
    }

    private SendFailure failureFrom(HttpStatusCode status, String body) {
        JsonNode error = jsonMapper.readTree(body.isBlank() ? "{}" : body).path("error");
        int code = error.path("code").asInt();
        if (code == NOT_DELIVERABLE) {
            return new SendFailure("This number is not on WhatsApp", false);
        }
        // Too many requests, or a problem on Meta's side: try again later
        boolean temporary = status.value() == 429 || status.is5xxServerError();
        String reason = error.path("message").asText("WhatsApp refused the message");
        return new SendFailure("WhatsApp: " + reason, temporary);
    }
}

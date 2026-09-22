package com.kayogx.eventcard.messaging.senders;

import com.kayogx.eventcard.billing.MessageChannel;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Sends SMS through Beem Africa (https://beem.africa).
 * One request per guest, so Beem's request id identifies the message in delivery reports.
 */
public class BeemSmsSender implements MessageSender {

    private final RestClient beem;
    private final JsonMapper jsonMapper;

    public BeemSmsSender(String apiKey, String secretKey, JsonMapper jsonMapper) {
        String login = Base64.getEncoder().encodeToString((apiKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        this.beem = RestClient.builder()
                .baseUrl("https://apisms.beem.africa/v1")
                .defaultHeader("Authorization", "Basic " + login)
                .build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    @Override
    public SendResult send(OutgoingMessage message) throws SendFailure {
        Map<String, Object> request = Map.of(
                "source_addr", message.smsSenderName(),
                "schedule_time", "",
                "encoding", 0,
                "message", message.text(),
                "recipients", List.of(Map.of("recipient_id", 1, "dest_addr", message.toPhone().replace("+", ""))));
        try {
            String answer = beem.post()
                    .uri("/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode result = jsonMapper.readTree(answer);
            if (!result.path("successful").asBoolean(false) || result.path("invalid").asInt(0) > 0) {
                throw new SendFailure("The SMS company refused this number: " + result.path("message").asText(), false);
            }
            return new SendResult(result.path("request_id").asText(), false);
        } catch (RestClientResponseException refused) {
            boolean temporary = refused.getStatusCode().value() == 429 || refused.getStatusCode().is5xxServerError();
            throw new SendFailure("The SMS company refused the message (" + refused.getStatusCode().value() + ")", temporary);
        } catch (RestClientException networkProblem) {
            throw new SendFailure("Could not reach the SMS company, trying again later", true);
        }
    }
}

package com.kayogx.eventcard.config;

import com.kayogx.eventcard.model.MessageChannel;
import com.kayogx.eventcard.service.BeemSmsSender;
import com.kayogx.eventcard.service.MessageSender;
import com.kayogx.eventcard.service.MetaWhatsAppSender;
import com.kayogx.eventcard.service.PretendSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Chooses the real or pretend connector for each channel, from the settings:
 *   app.messaging.whatsapp = pretend | meta
 *   app.messaging.sms      = pretend | beem
 */
@Component
@Slf4j
public class SendersSetup {

    private final Map<MessageChannel, MessageSender> senders;

    public SendersSetup(@Value("${app.messaging.whatsapp}") String whatsAppChoice,
                        @Value("${app.messaging.sms}") String smsChoice,
                        @Value("${app.messaging.meta.api-version}") String metaApiVersion,
                        @Value("${app.messaging.meta.phone-number-id}") String metaPhoneNumberId,
                        @Value("${app.messaging.meta.access-token}") String metaAccessToken,
                        @Value("${app.messaging.beem.api-key}") String beemApiKey,
                        @Value("${app.messaging.beem.secret-key}") String beemSecretKey,
                        JsonMapper jsonMapper) {
        MessageSender whatsApp = "meta".equals(whatsAppChoice)
                ? new MetaWhatsAppSender(metaApiVersion, metaPhoneNumberId, metaAccessToken, jsonMapper)
                : new PretendSender(MessageChannel.WHATSAPP);
        MessageSender sms = "beem".equals(smsChoice)
                ? new BeemSmsSender(beemApiKey, beemSecretKey, jsonMapper)
                : new PretendSender(MessageChannel.SMS);
        this.senders = Map.of(MessageChannel.WHATSAPP, whatsApp, MessageChannel.SMS, sms);
        log.info("Sending WhatsApp with '{}' and SMS with '{}'", whatsAppChoice, smsChoice);
    }

    public MessageSender senderFor(MessageChannel channel) {
        return senders.get(channel);
    }
}

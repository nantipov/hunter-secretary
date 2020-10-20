package org.nantipov.huntersecretary.services;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.LabelColor;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.nantipov.huntersecretary.domain.Assigment;
import org.nantipov.huntersecretary.domain.AssigmentSource;
import org.nantipov.huntersecretary.domain.AssigmentType;
import org.nantipov.huntersecretary.domain.AssignmentResponse;
import org.nantipov.huntersecretary.domain.ResponseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@Slf4j
@Service
public class GmailService {

    private static final String USER_ID = "me";

    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final SecretaryService secretaryService;

    private final String labelQuickThankYou;
    private final String labelReplyLater;
    private final String labelDone;
    private final boolean draftMode;

    private final List<String> workLabelNames;

    @Autowired
    public GmailService(HttpTransport httpTransport, JsonFactory jsonFactory,
                        SecretaryService secretaryService,
                        //TODO google properties
                        @Value("${hunter-secretary.google.gmail.labels.quick-thankyou}") String labelQuickThankYou,
                        @Value("${hunter-secretary.google.gmail.labels.reply-later}") String labelReplyLater,
                        @Value("${hunter-secretary.google.gmail.labels.done}") String labelDone,
                        @Value("${hunter-secretary.google.gmail.draft-mode}") boolean draftMode) {
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.secretaryService = secretaryService;
        this.labelQuickThankYou = labelQuickThankYou;
        this.labelReplyLater = labelReplyLater;
        this.labelDone = labelDone;
        this.workLabelNames = List.of(labelQuickThankYou, labelReplyLater, labelDone);
        this.draftMode = draftMode;
    }

    public void processMail(String token) {
        try {
            var gmail = gmail(token);
            var labelIdsByNames = ensureLabelsIds(gmail);
            labelIdsByNames.keySet()
                           .stream()
                           .filter(name -> !name.equals(labelDone))
                           .map(labelIdsByNames::get)
                           .map(labelId -> searchMessagesByLabelId(gmail, labelId))
                           .flatMap(List::stream)
                           .map(croppedMessage -> getMessage(gmail, croppedMessage.getId()))
                           .filter(Optional::isPresent)
                           .map(Optional::get)
                           .map(message -> createAssigment(labelIdsByNames, message))
                           .map(secretaryService::composeResponse)
                           .forEach(assignmentResponse -> respond(gmail, labelIdsByNames, assignmentResponse));
        } catch (IOException | IllegalStateException e) {
            log.error("Could not fetch email messages from Google account", e);
        }
    }

    private Gmail gmail(String token) {
        return new Gmail.Builder(
                httpTransport, jsonFactory,
                new Credential(BearerToken.authorizationHeaderAccessMethod())
                        .setAccessToken(token)
        ).build();
    }

    private List<Message> searchMessagesByLabelId(Gmail gmail, String labelId) {
        try {
            var response = gmail.users()
                                .messages()
                                .list(USER_ID)
                                .setLabelIds(List.of(labelId))
                                .setMaxResults(50L)
                                .execute();
            return response.getMessages() != null ? response.getMessages() : Collections.emptyList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private Map<String, String> ensureLabelsIds(Gmail gmail) throws IOException {
        var labelIdsByNames = new HashMap<String, String>();
        var nonexistentLabels = new ArrayList<>(workLabelNames);
        gmail.users()
             .labels()
             .list(USER_ID)
             .execute()
             .getLabels()
             .stream()
             .filter(label -> workLabelNames.contains(label.getName()))
             .forEach(label -> {
                 labelIdsByNames.put(label.getName(), label.getId());
                 nonexistentLabels.remove(label.getName());
             });

        var labelColor = new LabelColor().setTextColor("#434343").setBackgroundColor("#ffbc6b");
        nonexistentLabels.stream()
                         .map(name -> new Label().setColor(labelColor)
                                                 .setName(name)
                                                 .setType("USER")
                                                 .setMessageListVisibility("SHOW")
                                                 .setLabelListVisibility("LABEL_SHOW")
                         )
                         .map(label -> {
                                  try {
                                      return gmail.users()
                                                  .labels()
                                                  .create(USER_ID, label)
                                                  .execute();
                                  } catch (IOException e) {
                                      throw new IllegalStateException("Could not create a label", e);
                                  }
                              }
                         )
                         .forEach(newLabel -> labelIdsByNames.put(newLabel.getName(), newLabel.getId()));

        return labelIdsByNames;
    }

    private Optional<Message> getMessage(Gmail gmail, String id) {
        try {
            return Optional.of(gmail.users().messages().get(USER_ID, id).execute());
        } catch (IOException e) {
            log.error("Could not fetch email message from Google account: id '{}'", id);
            return Optional.empty();
        }
    }

    private Assigment createAssigment(Map<String, String> labelIdByNames, Message message) {
        var text = getMessageText(message);
        var language = Utils.getTextLanguage(text);
        return new Assigment(
                message.getId(),
                AssigmentSource.GMAIL,
                message.getLabelIds().contains(labelIdByNames.get(labelQuickThankYou)) ? AssigmentType.QUICK_THANK_YOU
                                                                                       : AssigmentType.REPLY_LATER,
                text,
                language,
                ResponseType.HTML, //TODO?
                ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(message.getInternalDate()), ZoneId.of(ZoneOffset.UTC.getId())
                )
        );
    }

    private String getMessageText(Message message) {
        var stringBuilder = new StringBuilder();
        return concatTextParts(stringBuilder, message.getPayload());
    }

    private String concatTextParts(StringBuilder builder, MessagePart messagePart) {
        if (messagePart.getMimeType().equalsIgnoreCase("text/plain")) {
            builder.append(new String(messagePart.getBody().decodeData()));
        }
        if (messagePart.getMimeType().equalsIgnoreCase("text/html")) {
            builder.append(Utils.extractTextFromHTML(new String(messagePart.getBody().decodeData())));
        }
        if (messagePart.getParts() != null && !messagePart.getParts().isEmpty()) {
            messagePart.getParts().forEach(part -> concatTextParts(builder, part));
        }
        return builder.toString();
    }

    public void respond(Gmail gmail, Map<String, String> labelIdsByNames, AssignmentResponse assignmentResponse) {
        try {
            var originMessage = getMessage(gmail, assignmentResponse.getAssigment().getMessageId()).orElseThrow();
            var newMessage = prepareMessage(assignmentResponse, originMessage);

            if (!draftMode) {
                gmail.users()
                     .messages()
                     .send(USER_ID, newMessage)
                     .execute();
            } else {
                gmail.users()
                     .drafts()
                     .create(USER_ID, new Draft().setMessage(newMessage))
                     .execute();
            }

            var labelsModifyRequest = new ModifyMessageRequest()
                    .setRemoveLabelIds(
                            labelIdsByNames.keySet()
                                           .stream()
                                           .filter(name -> !name.equals(labelDone))
                                           .map(labelIdsByNames::get).collect(Collectors.toList())
                    )
                    .setAddLabelIds(List.of(labelIdsByNames.get(labelDone)));
            gmail.users()
                 .messages()
                 .modify(USER_ID, originMessage.getId(), labelsModifyRequest)
                 .execute();
        } catch (IOException e) {
            log.error("Could not respond to email: {}", assignmentResponse.getAssigment().getMessageId(), e);
        }
    }

    private Message prepareMessage(AssignmentResponse assignmentResponse,
                                   Message originMessage) {
        try {
            var mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
            var headers =
                    originMessage.getPayload()
                                 .getHeaders()
                                 .stream()
                                 .collect(Collectors.toMap(
                                         header -> header.getName().toLowerCase(),
                                         header -> List.of(header.getValue()),
                                         (list1, list2) ->
                                                 ImmutableList.<String>builder()
                                                         .addAll(list1)
                                                         .addAll(list2)
                                                         .build()
                                 ));
            var subject = Optional.ofNullable(headers.get("subject"))
                                  .map(list -> list.get(0))
                                  .map(subj -> "Re: " + subj)
                                  .orElse(null);

            var from = Optional.ofNullable(headers.get("from"))
                               .map(list -> list.get(0))
                               .orElse("");

            var replyTo = Optional.ofNullable(headers.get("reply-to"))
                                  .map(list -> list.get(0))
                                  .orElse(null);

            mimeMessage.setSubject(subject, StandardCharsets.UTF_8.toString());
            var recipient = new InternetAddress(replyTo != null ? replyTo : from);
            recipient.setPersonal(recipient.getPersonal(), StandardCharsets.UTF_8.toString());
            mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO, recipient);
            mimeMessage.setContent(assignmentResponse.getResponse(), "text/html; charset=UTF-8");
            var buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            byte[] bytes = buffer.toByteArray();
            String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
            return new Message().setRaw(encodedEmail)
                                .setThreadId(originMessage.getThreadId());
        } catch (IOException | MessagingException e) {
            throw new IllegalArgumentException("Could not compose email message", e);
        }
    }
}

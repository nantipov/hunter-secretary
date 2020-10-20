package org.nantipov.huntersecretary.services;

import freemarker.template.TemplateException;
import lombok.Data;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.nantipov.huntersecretary.domain.Assigment;
import org.nantipov.huntersecretary.domain.AssigmentType;
import org.nantipov.huntersecretary.domain.AssignmentResponse;
import org.nantipov.huntersecretary.domain.ResponseType;
import org.nantipov.huntersecretary.domain.responsecomponent.PlainResponse;
import org.nantipov.huntersecretary.domain.responsecomponent.ResponseComponent;
import org.nantipov.huntersecretary.domain.responsecomponent.ResponseLanguage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SecretaryService {

    //TODO application settings?
    private static final Duration LATE_RESPONSE_DURATION = Duration.ofDays(14);
    private static final String RESPONSE_TEMPLATE = "response/base.ftl";

    private final FreeMarkerConfigurer freeMarkerConfigurer;

    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public SecretaryService(FreeMarkerConfigurer freeMarkerConfigurer) {
        this.freeMarkerConfigurer = freeMarkerConfigurer;
    }

    public AssignmentResponse composeResponse(Assigment assigment) {

        var components = getComponents(assigment);
        var responseString = writeResponse(assigment, components);
        return new AssignmentResponse(assigment, responseString);
    }

    private String writeResponse(Assigment assigment, List<ResponseComponent> components) {
        try {
            var template = freeMarkerConfigurer.getConfiguration()
                                               .getTemplate(RESPONSE_TEMPLATE, assigment.getLanguage().getLocale());
            var writer = new StringWriter();
            template.process(new TemplateData(components), writer);
            if (assigment.getResponseType() == ResponseType.HTML) {
                return renderToHTML(writer.toString());
            } else {
                //TODO markdown-to-text / de-markdown?
                return writer.toString();
            }
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("Could not compose response", e);
        }
    }

    private String renderToHTML(String markdownText) {
        return htmlRenderer.render(markdownParser.parse(markdownText));
    }

    private List<ResponseComponent> getComponents(Assigment assigment) {
        var components = new ArrayList<ResponseComponent>();

        components.add(ResponseComponent.of(PlainResponse.GREETINGS, assigment.getLanguage()));
        if (assigment.getLanguage() != ResponseLanguage.ENGLISH) {
            components.add(ResponseComponent.of(PlainResponse.ENGLISH_VERSION_BELOW, ResponseLanguage.ENGLISH));
        }

        if (
                Duration.between(
                        assigment.getMessageTimestamp(),
                        ZonedDateTime.now(ZoneId.of(ZoneOffset.UTC.getId()))
                ).compareTo(LATE_RESPONSE_DURATION) > 0
        ) {
            components.add(ResponseComponent.of(PlainResponse.LATE_RESPONSE, assigment.getLanguage()));
        }

        if (assigment.getType() != AssigmentType.QUICK_THANK_YOU) {
            throw new UnsupportedOperationException(assigment.getType().toString());
        }

        components.add(ResponseComponent.of(PlainResponse.THANK_YOU, assigment.getLanguage()));
        components.add(ResponseComponent.of(PlainResponse.FAREWELL, assigment.getLanguage()));

        addExtraEnglishBelowComponents(components);
        return components;
    }

    private void addExtraEnglishBelowComponents(List<ResponseComponent> components) {
        var isEnglishBelow =
                components.stream()
                          .anyMatch(c -> c.getTemplateName()
                                          .equals(PlainResponse.ENGLISH_VERSION_BELOW.getTemplateName())
                          );
        if (isEnglishBelow) {
            var extraComponents =
                    components.stream()
                              .filter(c -> c.getLanguage() != ResponseLanguage.ENGLISH)
                              .map(c -> new ResponseComponent(c.getTemplateName(), ResponseLanguage.ENGLISH))
                              .collect(Collectors.toList());
            if (!extraComponents.isEmpty()) {
                components.add(new ResponseComponent(
                        PlainResponse.ENGLISH_VERSION_HEADER.getTemplateName(), ResponseLanguage.ENGLISH
                ));
            }
            components.addAll(extraComponents);
        }
    }

    @Data
    public static class TemplateData {
        private final List<ResponseComponent> components;
    }
}

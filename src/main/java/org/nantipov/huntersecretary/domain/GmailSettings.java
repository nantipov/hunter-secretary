package org.nantipov.huntersecretary.domain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "hunter-secretary.google.gmail")
@Component
public class GmailSettings {
    private Map<String, String> labels;
    private boolean draftMode;
}

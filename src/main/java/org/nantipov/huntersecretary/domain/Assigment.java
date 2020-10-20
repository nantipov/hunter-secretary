package org.nantipov.huntersecretary.domain;

import lombok.Data;
import org.nantipov.huntersecretary.domain.responsecomponent.ResponseLanguage;

import java.time.ZonedDateTime;

@Data
public class Assigment {
    private final String messageId;
    private final AssigmentSource source;
    private final AssigmentType type;
    private final String text; //TODO?
    private final ResponseLanguage language;
    private final ResponseType responseType;
    private final ZonedDateTime messageTimestamp;
}

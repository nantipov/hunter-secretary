package org.nantipov.huntersecretary.domain.responsecomponent;

import lombok.Data;

@Data
public class ResponseComponent {
    private final String templateName;
    private final ResponseLanguage language;

    public static ResponseComponent of(PlainResponse plainResponse, ResponseLanguage language) {
        return new ResponseComponent(plainResponse.getTemplateName(), language);
    }
}

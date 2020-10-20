package org.nantipov.huntersecretary.domain.responsecomponent;

public enum PlainResponse {
    GREETINGS("greetings"),
    THANK_YOU("thank-you"),
    ENGLISH_VERSION_BELOW("english-version-below"),
    ENGLISH_VERSION_HEADER("english-version-header"),
    LATE_RESPONSE("sorry-for-late-response"),
    FAREWELL("farewell");

    private String templateName;

    PlainResponse(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateName() {
        return templateName;
    }
}

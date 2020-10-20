package org.nantipov.huntersecretary.domain.responsecomponent;

import java.util.Locale;

public enum ResponseLanguage {
    ENGLISH("en", Locale.ENGLISH),
    RUSSIAN("ru", Locale.forLanguageTag("ru")),
    GERMAN("de", Locale.GERMAN);

    private String folderName;
    private Locale locale;

    ResponseLanguage(String folderName, Locale locale) {
        this.folderName = folderName;
        this.locale = locale;
    }

    public String getFolderName() {
        return folderName;
    }

    public Locale getLocale() {
        return locale;
    }
}

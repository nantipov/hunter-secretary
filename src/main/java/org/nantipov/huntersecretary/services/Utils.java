package org.nantipov.huntersecretary.services;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.nantipov.huntersecretary.domain.responsecomponent.ResponseLanguage;
import org.springframework.ui.Model;

public class Utils {

    private static final String TEMPLATE_WEB_BASE = "web/base";

    public static final String TEMPLATE_WEB_HOME = "home";
    public static final String TEMPLATE_WEB_POLICY = "policy";
    public static final String TEMPLATE_WEB_AUTHORIZED = "google-authorized";

    private static final String VARIABLE_TEMPLATE = "templateName";

    private static final LanguageDetector LANGUAGE_DETECTOR =
            LanguageDetectorBuilder.fromLanguages(
                    Language.ENGLISH,
                    Language.RUSSIAN,
                    Language.GERMAN
            ).build();

    private Utils() {

    }

    public static String templateWeb(String template, Model model) {
        model.addAttribute(VARIABLE_TEMPLATE, template);
        return TEMPLATE_WEB_BASE;
    }

    public static ResponseLanguage getTextLanguage(String text) {
        var language = LANGUAGE_DETECTOR.detectLanguageOf(text);
        switch (language) {
            case GERMAN:
                return ResponseLanguage.GERMAN;
            case RUSSIAN:
                return ResponseLanguage.RUSSIAN;
            default:
                return ResponseLanguage.ENGLISH;
        }
    }
}

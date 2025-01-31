package com.frc.codex.model.i18n;

import static java.util.Objects.requireNonNull;
import java.util.Map;

public class TranslationsEntry {
    private String language;
    private String namespace;
    private Map<String, Object> translations;

    public TranslationsEntry(String language, String namespace, Map<String, Object> translations) {
        this.language = requireNonNull(language);
        this.namespace = requireNonNull(namespace);
        this.translations = Map.copyOf(translations);
    }

    public String getLanguage() {
        return language;
    }

    public String getNamespace() {
        return namespace;
    }

    public Map<String, Object> getTranslations() {
        return translations;
    }
}

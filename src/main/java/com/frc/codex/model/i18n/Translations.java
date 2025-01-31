package com.frc.codex.model.i18n;

import java.util.HashMap;
import java.util.Map;

public class Translations {
    private Map<String, Map<String, Map<String, Object>>> translationsByLangByNamespace;

    private Translations(Builder b) {
        this.translationsByLangByNamespace = Map.copyOf(b.translationsByLangByNamespace);
    }

    public Map<String, Map<String, Map<String, Object>>> getMessages() {
        return translationsByLangByNamespace;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, Map<String, Map<String, Object>>> translationsByLangByNamespace =
                new HashMap<>();

        public Translations build() {
            return new Translations(this);
        }

        public Builder addEntry(TranslationsEntry translationsEntry) {
            this.translationsByLangByNamespace
                    .computeIfAbsent(translationsEntry.getLanguage(), k -> new HashMap<>())
                    .put(translationsEntry.getNamespace(), translationsEntry.getTranslations());
            return this;
        }
    }
}

package com.frc.codex.i18n.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frc.codex.i18n.MessageService;
import com.frc.codex.model.i18n.Translations;
import com.frc.codex.model.i18n.TranslationsEntry;

@Component
public class MessageServiceImpl implements MessageService {
    private static final String I18N_PATH = "classpath:i18n/*/*.json";
    private final Translations translations;

    public MessageServiceImpl() {
        this.translations = loadTranslations();
    }

    private Translations loadTranslations() {
        ObjectMapper objectMapper = new ObjectMapper();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Translations.Builder translationsBuilder = Translations.builder();

        try {
            Resource[] resources = resolver.getResources(I18N_PATH);

            for (Resource resource : resources) {
                String path = resource.getURI().toString();

                String[] parts = path.split("/i18n/")[1].split("/");
                String language = parts[0];
                String namespace = parts[1].replace(".json", "");

                try (InputStream inputStream = resource.getInputStream()) {
                    Map<String, Object> translations = objectMapper.readValue(inputStream, new TypeReference<>() {});
                    TranslationsEntry translationsEntry = new TranslationsEntry(language, namespace, translations);
                    translationsBuilder.addEntry(translationsEntry);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load translation files", e);
        }
        return translationsBuilder.build();
    }

    public Translations getTranslations() {
        return translations;
    }
}

package com.frc.codex.controllers;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.frc.codex.i18n.MessageService;
import com.frc.codex.model.i18n.Translations;

@ControllerAdvice
public class GlobalControllerAdvice {
    private final MessageService messageService;

    public GlobalControllerAdvice(MessageService messageService) {
        this.messageService = messageService;
    }

    @ModelAttribute
    public void addTranslations(Model model) {
        Translations translations = messageService.getTranslations();
        model.addAttribute("translations", translations.getMessages());
    }
}

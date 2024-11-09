package com.frc.codex.model;

public enum OimFormat {
    CSV("CSV"),
    JSON("JSON");

    private final String format;

    OimFormat(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public static OimFormat fromFormat(String format) {
        for (OimFormat f : values()) {
            if (f.getFormat().equals(format.toUpperCase())) {
                return f;
            }
        }
        return null;
    }
}

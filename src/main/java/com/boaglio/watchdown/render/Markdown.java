package com.boaglio.watchdown.render;

/** Escaping for text that comes from the video: titles, channel names, and transcript lines. */
public final class Markdown {

    private static final String SENSITIVE = "\\`*_[]<>|";

    private Markdown() {
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (char character : text.toCharArray()) {
            if (SENSITIVE.indexOf(character) >= 0) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    /** A double-quoted YAML scalar, safe for the front matter of the generated files. */
    public static String yaml(String text) {
        String value = text == null ? "" : text;
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + '"';
    }
}

package com.boaglio.watchdown.render;

import java.text.Normalizer;
import java.util.Locale;

/** Turns a video title into the ASCII folder name used under the output directory. */
public final class Slugs {

    private static final int MAX_LENGTH = 60;
    private static final String FALLBACK = "video";

    private Slugs() {
    }

    public static String slugify(String title) {
        if (title == null || title.isBlank()) {
            return FALLBACK;
        }
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace("ß", "ss")
                .replace("ø", "o")
                .replace("Ø", "O")
                .replace("æ", "ae")
                .replace("Æ", "AE")
                .replace("đ", "d")
                .replace("ł", "l");

        StringBuilder slug = new StringBuilder(ascii.length());
        for (char character : ascii.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9') {
                slug.append(character);
            } else if (slug.isEmpty() || slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
        }

        String result = slug.toString();
        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH);
        }
        result = trimDashes(result);
        return result.isEmpty() ? FALLBACK : result;
    }

    private static String trimDashes(String value) {
        int from = 0;
        int to = value.length();
        while (from < to && value.charAt(from) == '-') {
            from++;
        }
        while (to > from && value.charAt(to - 1) == '-') {
            to--;
        }
        return value.substring(from, to);
    }
}

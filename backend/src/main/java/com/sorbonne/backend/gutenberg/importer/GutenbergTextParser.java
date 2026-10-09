package com.sorbonne.backend.gutenberg.importer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class GutenbergTextParser {

    private static final Pattern TITLE = Pattern.compile("(?mi)^Title:\\s*(.+?)\\s*$");
    private static final Pattern AUTHOR = Pattern.compile("(?mi)^Author:\\s*(.+?)\\s*$");
    private static final Pattern LANGUAGE = Pattern.compile("(?mi)^Language:\\s*(.+?)\\s*$");
    private static final Pattern START = Pattern.compile(
            "(?mi)^\\*\\*\\*\\s*START OF THE PROJECT GUTENBERG (?:EBOOK|EBOOKS).*?\\*\\*\\*\\s*$");
    private static final Pattern END = Pattern.compile(
            "(?mi)^\\*\\*\\*\\s*END OF THE PROJECT GUTENBERG (?:EBOOK|EBOOKS).*?\\*\\*\\*\\s*$");

    public ParsedBook parse(int gutenbergId, String sourceUrl, String rawText) {
        String title = firstGroup(TITLE, rawText, "Gutenberg book " + gutenbergId);
        String author = firstGroup(AUTHOR, rawText, null);
        String language = normalizeLanguage(firstGroup(LANGUAGE, rawText, null));
        String body = extractBody(rawText);
        return new ParsedBook(gutenbergId, title, author, language, sourceUrl, body);
    }

    private String extractBody(String rawText) {
        Matcher start = START.matcher(rawText);
        if (!start.find()) {
            return rawText.strip();
        }

        int bodyStart = start.end();
        Matcher end = END.matcher(rawText);
        if (end.find(bodyStart)) {
            return rawText.substring(bodyStart, end.start()).strip();
        }
        return rawText.substring(bodyStart).strip();
    }

    private String firstGroup(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).strip() : fallback;
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return null;
        }
        String normalized = language.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "english" -> "en";
            case "french" -> "fr";
            case "german" -> "de";
            case "spanish" -> "es";
            case "italian" -> "it";
            default -> normalized.length() <= 32 ? normalized : normalized.substring(0, 32);
        };
    }

    public record ParsedBook(
            int gutenbergId,
            String title,
            String author,
            String language,
            String sourceUrl,
            String text) {
    }
}

package com.sorbonne.backend.catalog;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, offline extraction from explicit headers and conventional title pages. */
@Component
public final class LocalBookMetadataExtractor {
    private static final int SAMPLE_BYTES = 65536;
    private static final Pattern OPAQUE_KEY = Pattern.compile("(?i)^[0-9a-f]{32,128}$");
    private static final Pattern START = Pattern.compile(
            "(?im)^\\s*\\*{3}\\s*START OF (?:THE |THIS )?PROJECT GUTENBERG.*?\\*{3}[^\\n]*");
    private static final Pattern BODY = Pattern.compile(
            "(?im)^\\h*(?:contents|table of contents|table des matières|inhalt|inhaltsverzeichnis|"
                    + "preface|foreword|introduction|préface|avant-propos|einleitung|"
                    + "chapter(?:\\h+.+)?|chapitre(?:\\h+.+)?|kapitel(?:\\h+.+)?)\\h*[.:]?\\h*$");
    private static final Pattern BYLINE = Pattern.compile("(?iu)^(?:by|par|von|por|di|av)\\h*(?::|\\h)\\h*(.*)$");
    private static final Pattern BY_ALONE = Pattern.compile("(?iu)^(?:by|par|von|por|di|av)\\h*:?$");
    private static final Pattern CREDIT = Pattern.compile(
            "(?iu)^(?:produced by|transcribed\\b|distributed proofreading|"
                    + "the online distributed|prepared by|e-?text prepared|this (?:e-?book|e-?text|file|text)|"
                    + "transcriber[’']?s? (?:note|amendments)|note(?:s)?(?:\\h|:)|"
                    + "typographical errors|(?:a few(?: minor)?|obvious) typographical|there are two volumes|"
                    + "variations in spelling|words (?:italicized|with bold)|we are indebted|"
                    + "copyright|all rights reserved|release date|posting date|"
                    + "character ?set ?encoding|\\[?illustration|\\[?picture|\\[?image|\\[?cover|"
                    + "https?://|www\\.).*");
    private static final Pattern GUTENBERG_HEADING = Pattern.compile(
            "(?is)^(?:the\\s+)?project gutenberg(?:[’']s|\\s+(?:e-?book|e-?text))\\s+(?:of\\s+)?(.+)$");
    private static final Pattern EBOOK_ID = Pattern.compile(
            "(?i)\\[(?:project gutenberg\\h+)?e-?(?:book|text)\\h*(?:no\\.?\\h*|number\\h*|#\\h*)([1-9][0-9]*)\\]");
    private static final Pattern GUTENBERG_URL = Pattern.compile(
            "(?i)https?://(?:www\\.)?gutenberg\\.org/(?:ebooks/|(?:cache/)?epub/)([1-9][0-9]*)(?=[/\\s?#.,;)]|$)");

    public CatalogCsvReader.Entry infer(String storageKey, Path file) {
        final String sample;
        try (InputStream input = Files.newInputStream(file)) {
            sample = new String(input.readNBytes(SAMPLE_BYTES), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect local book: " + storageKey, failure);
        }
        // Never search the whole novel for a byline, URL or an embedded book's metadata.
        String front = sample.substring(0, Math.min(sample.length(), 16384));
        Matcher start = START.matcher(front);
        String explicit = start.find() ? front.substring(0, start.start()) : beforeBody(front);
        String title = field(explicit, "Title|Titre", true);
        String author = field(explicit, "Author|Authors|Auteur|Auteurs", true);
        String language = LocalBookLanguageDetector.languageCode(field(explicit, "Language|Langue", false));
        String source = sourceUrl(explicit);

        TitlePage page = titlePage(front);
        if (title == null) title = page.title();
        if (author == null) author = page.author();
        if (language == null) language = LocalBookLanguageDetector.detect(sample);
        if (title == null) title = filenameLabel(storageKey);
        return new CatalogCsvReader.Entry(storageKey, title, author, language, source);
    }

    private static String beforeBody(String text) {
        Matcher boundary = BODY.matcher(text);
        return boundary.find() ? text.substring(0, boundary.start()) : text;
    }

    private static String field(String text, String labels, boolean multiline) {
        Pattern pattern = Pattern.compile("(?im)^\\h*(?:" + labels + "):\\h*([^\\n]*)");
        Matcher match = pattern.matcher(text);
        if (!match.find()) return null;
        String value = match.group(1).strip();
        if (multiline) {
            // Gutenberg wraps long titles/authors using indented continuation lines.
            String rest = text.substring(Math.min(match.end() + 1, text.length()));
            for (String line : rest.split("\n", -1)) {
                if (line.isBlank() || !Character.isWhitespace(line.charAt(0)) || line.contains(":")) break;
                value += " " + line.strip();
                if (value.length() > 500) break;
            }
        }
        return value.isBlank() ? null : clean(value);
    }

    private static String sourceUrl(String header) {
        String explicit = field(header, "Source(?:_url| URL)?|URL|Original URL", false);
        if (explicit != null) {
            try {
                URI uri = URI.create(explicit);
                if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null && uri.getUserInfo() == null) return uri.toString();
            } catch (IllegalArgumentException ignored) { /* Try a Gutenberg identifier instead. */ }
        }
        Matcher id = EBOOK_ID.matcher(header);
        if (id.find()) return "https://www.gutenberg.org/ebooks/" + id.group(1);
        // Restrict URL inference to the Gutenberg preamble, excluding credit/donation links.
        Matcher heading = Pattern.compile("(?im)^(?:the )?project gutenberg.*$").matcher(header);
        if (heading.find()) {
            Matcher url = GUTENBERG_URL.matcher(header.substring(heading.start()));
            if (url.find()) return "https://www.gutenberg.org/ebooks/" + url.group(1);
        }
        return null;
    }

    private static TitlePage titlePage(String front) {
        String gutenbergTitle = null;
        String gutenbergAuthor = null;
        // A Gutenberg banner belongs at the top, not in a discussion later in the book.
        String opening = beforeBody(front.substring(0, Math.min(front.length(), 2000)));
        for (String paragraph : opening.split("\\n\\h*\\n")) {
            Matcher heading = GUTENBERG_HEADING.matcher(paragraph.strip());
            if (heading.matches()) {
                String value = clean(heading.group(1)).replaceFirst("\\s*\\(#.*$", "");
                Matcher by = Pattern.compile("(?i)^(.+?),?\\s+by\\s+(.+)$").matcher(value);
                if (by.matches()) {
                    String candidate = by.group(1).replaceFirst(",$", "").strip();
                    if (plausibleTitle(candidate, 1)) gutenbergTitle = candidate;
                    String name = by.group(2).replaceFirst("\\s*\\(#.*$", "").strip();
                    if (plausibleAuthor(name)) gutenbergAuthor = name;
                } else if (plausibleTitle(value, 1)) gutenbergTitle = value;
                break;
            }
        }
        Matcher start = START.matcher(front);
        if (start.find()) front = front.substring(start.end());
        front = beforeBody(front);
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : front.split("\\n\\h*\\n")) {
            String block = paragraph.strip();
            if (block.isEmpty() || !block.codePoints().anyMatch(Character::isLetter)) continue;
            String cleaned = clean(block).replaceFirst("^\\[", "");
            if (CREDIT.matcher(cleaned).matches()) continue;
            if (block.endsWith("***")) continue; // fragment left by a stripped Gutenberg banner
            if (GUTENBERG_HEADING.matcher(block).matches()) continue;
            if (block.matches("(?is)^(?:title|titre|author|auteur|language|langue|source|url):.*")) continue;
            paragraphs.add(block);
            if (paragraphs.size() >= 20) break;
        }
        String title = null;
        String author = null;
        for (int p = 0; p < paragraphs.size(); p++) {
            String block = paragraphs.get(p);
            String[] lines = block.split("\n");
            List<String> titleLines = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                String line = clean(lines[i]);
                if (line.matches("(?iu)^\\(?(?:translated|traduit|übersetzt|tradotto)\\b.*")) {
                    // Some title pages put an unlabelled author immediately before a translator.
                    if (titleLines.size() >= 2 && personName(titleLines.getLast())) {
                        author = titleLines.removeLast();
                    }
                    break;
                }
                Matcher by = BYLINE.matcher(line);
                if (by.matches() || BY_ALONE.matcher(line).matches()) {
                    String candidate = by.matches() ? by.group(1).strip() : "";
                    if (candidate.isEmpty() && i + 1 < lines.length) candidate = clean(lines[i + 1]);
                    if (candidate.isEmpty() && p + 1 < paragraphs.size()) candidate = clean(paragraphs.get(p + 1));
                    if (plausibleAuthor(candidate)) {
                        author = candidate;
                        String adjacent = titleBeforeByline(paragraphs, p, titleLines);
                        if (adjacent != null) title = adjacent;
                    }
                    break;
                }
                titleLines.add(line);
            }
            if (title == null && p == 0 && (front.contains("\n\n") || author != null)) {
                String candidate = String.join(" ", titleLines);
                if (plausibleTitle(candidate, titleLines.size())) {
                    title = candidate;
                    if (candidate.matches("(?iu)^(?:the|a|an|le|la|les|l[’']|un|une|der|die|das)$")
                            && p + 1 < paragraphs.size()) {
                        String next = clean(paragraphs.get(p + 1));
                        if (plausibleTitle(next, 1)) title += " " + next;
                    }
                }
            }
            if (author != null || block.length() > 600 || lines.length > 10) break;
        }
        return new TitlePage(gutenbergTitle == null ? title : gutenbergTitle,
                gutenbergAuthor == null ? author : gutenbergAuthor);
    }

    private static boolean plausibleTitle(String value, int lines) {
        if (value.isBlank() || value.length() > 300 || lines > 6 || value.split("\\s+").length > 40) return false;
        if (!Character.isLetterOrDigit(value.codePointAt(0)) || value.contains("://") || value.contains("***")) return false;
        if (Character.isLowerCase(value.codePointAt(0))) return false;
        if (value.matches(".*[.!?]\\s+[\\p{Ll}].*")) return false;
        if (value.matches("(?iu)^Σημείωση[:\\h].*")) return false;
        if (value.matches("(?iu)^(?:page \\d+|œuvres(?: posthumes)?(?: de .*)?|works of .*)[.:]?")) return false;
        if (value.matches("(?iu)^(?:single copies|annual subscription|wholesale agents|a sequel)\\b.*")) return false;
        if (value.matches("(?iu)^(?:by|par|von|por|di|av|translated|edited|traduit|volume|tome|book|part)\\b.*")) return false;
        // A prose sentence is not a title. All-capital title pages commonly end in a period.
        return !value.matches(".*[.!?;]$") || value.equals(value.toUpperCase(Locale.ROOT));
    }

    private static String titleBeforeByline(List<String> paragraphs, int position, List<String> currentLines) {
        String current = String.join(" ", currentLines);
        if (plausibleTitle(current, currentLines.size())) return current;
        for (int p = position - 1; p >= Math.max(0, position - 3); p--) {
            String candidate = clean(paragraphs.get(p));
            // These are subtitles/edition details, not a replacement for the main title.
            if (candidate.matches("(?iu)^(?:being|a (?:novel|romance|chronicle|complete)|illustrated|"
                    + "from|volume|tome|vol\\.|second|first|third|new edition)\\b.*")) continue;
            if (!plausibleTitle(candidate, paragraphs.get(p).split("\n").length)) return null;
            if (p > 0) {
                String previous = clean(paragraphs.get(p - 1));
                if (previous.matches("(?iu)^(?:the|a|an|le|la|les|l[’']|un|une|der|die|das)$")
                        || previous.matches("(?iu).+\\h(?:of|de|du|des)$")) {
                    candidate = previous + " " + candidate;
                }
            }
            return candidate;
        }
        return null;
    }

    private static boolean personName(String value) {
        // Only use unlabelled names when a translator credit supplies additional context.
        return plausibleAuthor(value) && value.matches("(?:\\p{Lu}[\\p{Ll}’'-]+\\h+){1,3}\\p{Lu}[\\p{Ll}’'-]+");
    }

    private static boolean plausibleAuthor(String value) {
        if (value.isBlank() || value.length() > 160 || value.split("\\s+").length > 16) return false;
        if (Character.isLowerCase(value.codePointAt(0))) return false;
        if (value.contains(":") || value.contains("http") || value.matches(".*[!?;].*")) return false;
        if (CREDIT.matcher(value).matches() || BY_ALONE.matcher(value).matches()) return false;
        // Reject prose and publisher credits rather than assigning them as an author.
        return !value.matches("(?iu)^(?:the|a|an|le|la|les|un|une|this|that|his|her)\\b.*")
                && !value.matches("(?iu).*(?:university press|publishing|publishers|copyright|proofreading|"
                        + "company|libraire|éditeur|imprimerie).*");
    }

    private static String clean(String value) {
        return value.replaceAll("\\s+", " ").strip().replaceAll("^[_*]+|[_*]+$", "").strip();
    }

    static String filenameLabel(String key) {
        String filename = Path.of(key).getFileName().toString();
        String stem = filename.substring(0, filename.length() - 4);
        if (OPAQUE_KEY.matcher(stem).matches()) return "Titre inconnu [" + stem.substring(0, 12) + "]";
        String label = stem.replace('_', ' ').replaceAll("\\s+", " ").strip();
        return label.isBlank() ? "Livre sans titre" : label;
    }

    private record TitlePage(String title, String author) {}
}

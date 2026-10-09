package com.sorbonne.backend.gutenberg.harvest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.SSLHandshakeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sorbonne.backend.gutenberg.GutenbergProperties;

@Component
public class GutenbergHarvestClient {

    private static final Logger log = LoggerFactory.getLogger(GutenbergHarvestClient.class);

    private static final Pattern LINK = Pattern.compile("(?i)href=[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern NEXT = Pattern.compile(
            "(?is)href=[\\\"']([^\\\"']+)[\\\"'][^>]*>\\s*Next Page\\s*</a>");
    private static final Pattern GUTENBERG_ID = Pattern.compile("/(\\d+)(?:-\\d+)?\\.zip$");

    private final HttpClient httpClient;
    private final long maxUncompressedBytes;

    public GutenbergHarvestClient(GutenbergProperties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.maxUncompressedBytes = properties.maxUncompressedBytes();
    }

    public HarvestPage fetchPage(String pageUrl) throws IOException, InterruptedException {
        URI pageUri = URI.create(pageUrl);
        HttpRequest request = HttpRequest.newBuilder(pageUri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "DAAR-SearchEngine/1.0 (Sorbonne academic project)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ensureSuccess(response.statusCode(), pageUrl);
        return parseHarvestPage(pageUri, response.body());
    }

    public String downloadText(HarvestLink link) throws IOException, InterruptedException {
        URI originalUri = URI.create(link.url());
        HttpResponse<InputStream> response;
        URI effectiveUri = originalUri;

        try {
            response = sendArchiveRequest(originalUri);
        } catch (SSLHandshakeException exception) {
            if (!isAlephHttps(originalUri)) {
                throw exception;
            }

            effectiveUri = toHttp(originalUri);
            log.warn(
                    "TLS certificate mismatch on aleph.gutenberg.org; retrying the official harvest archive over HTTP: {}",
                    effectiveUri);
            response = sendArchiveRequest(effectiveUri);
        }

        ensureSuccess(response.statusCode(), effectiveUri.toString());

        try (InputStream body = response.body(); ZipInputStream zip = new ZipInputStream(body)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".txt")) {
                    byte[] bytes = readLimited(zip, maxUncompressedBytes);
                    return decodeText(bytes);
                }
            }
        }

        throw new IOException("No .txt entry found in " + effectiveUri);
    }

    private HttpResponse<InputStream> sendArchiveRequest(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "DAAR-SearchEngine/1.0 (Sorbonne academic project)")
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    boolean isAlephHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && "aleph.gutenberg.org".equalsIgnoreCase(uri.getHost());
    }

    URI toHttp(URI uri) {
        StringBuilder value = new StringBuilder("http://").append(uri.getRawAuthority()).append(uri.getRawPath());
        if (uri.getRawQuery() != null) {
            value.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            value.append('#').append(uri.getRawFragment());
        }
        return URI.create(value.toString());
    }

    HarvestPage parseHarvestPage(URI pageUri, String html) throws IOException {
        Map<Integer, HarvestLink> uniqueBooks = new LinkedHashMap<>();
        Matcher matcher = LINK.matcher(html);

        while (matcher.find()) {
            String rawHref = unescapeHtml(matcher.group(1));
            URI resolved = pageUri.resolve(rawHref);
            String url = resolved.toString();
            Matcher idMatcher = GUTENBERG_ID.matcher(resolved.getPath());
            if (!idMatcher.find()) {
                continue;
            }

            int id = Integer.parseInt(idMatcher.group(1));
            HarvestLink candidate = new HarvestLink(id, url);
            HarvestLink current = uniqueBooks.get(id);

            if (current == null || isPreferred(candidate, current)) {
                uniqueBooks.put(id, candidate);
            }
        }

        Matcher nextMatcher = NEXT.matcher(html);
        String nextPage = null;
        if (nextMatcher.find()) {
            nextPage = pageUri.resolve(unescapeHtml(nextMatcher.group(1))).toString();
        }

        if (uniqueBooks.isEmpty()) {
            throw new IOException("No Gutenberg text archive links found on harvest page " + pageUri);
        }

        return new HarvestPage(new ArrayList<>(uniqueBooks.values()), nextPage);
    }

    private boolean isPreferred(HarvestLink candidate, HarvestLink current) {
        String canonicalSuffix = "/" + candidate.gutenbergId() + ".zip";
        return candidate.url().endsWith(canonicalSuffix) && !current.url().endsWith(canonicalSuffix);
    }

    private byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Uncompressed Gutenberg text exceeds configured limit: " + maxBytes);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return StandardCharsets.ISO_8859_1.decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private String unescapeHtml(String href) {
        return href.replace("&amp;", "&");
    }

    private void ensureSuccess(int statusCode, String url) throws IOException {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + " while fetching " + url);
        }
    }

    public record HarvestPage(List<HarvestLink> links, String nextPageUrl) {
    }

    public record HarvestLink(int gutenbergId, String url) {
    }
}

package com.sorbonne.backend.gutenberg.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.sorbonne.backend.gutenberg.GutenbergProperties;

class GutenbergHarvestClientTest {

    @Test
    void deduplicatesEncodingVariantsAndReadsNextPage() throws Exception {
        GutenbergProperties properties = new GutenbergProperties(
                "https://www.gutenberg.org/robot/harvest?filetypes[]=txt&langs[]=en",
                10_000,
                2_000,
                25_000_000,
                new GutenbergProperties.Api("https://example.test", "example.test", "", 50));

        GutenbergHarvestClient client = new GutenbergHarvestClient(properties);

        String html = """
                <html><body>
                  <a href="https://aleph.gutenberg.org/1/0/0/8/10084/10084-8.zip">a</a>
                  <a href="https://aleph.gutenberg.org/1/0/0/8/10084/10084.zip">b</a>
                  <a href="https://aleph.gutenberg.org/1/5/5/1554/1554.zip">c</a>
                  <a href="/robot/harvest?filetypes%5B%5D=txt&amp;langs%5B%5D=en&amp;offset=100">Next Page</a>
                </body></html>
                """;

        var page = client.parseHarvestPage(
                URI.create("https://www.gutenberg.org/robot/harvest?filetypes%5B%5D=txt&langs%5B%5D=en"),
                html);

        assertEquals(2, page.links().size());
        assertEquals(10084, page.links().getFirst().gutenbergId());
        assertEquals("https://aleph.gutenberg.org/1/0/0/8/10084/10084.zip", page.links().getFirst().url());
        assertEquals(
                "https://www.gutenberg.org/robot/harvest?filetypes%5B%5D=txt&langs%5B%5D=en&offset=100",
                page.nextPageUrl());
    }

    @Test
    void downgradesOnlyAlephHttpsLinksToHttp() {
        GutenbergProperties properties = new GutenbergProperties(
                "https://www.gutenberg.org/robot/harvest?filetypes[]=txt&langs[]=en",
                10_000,
                2_000,
                25_000_000,
                new GutenbergProperties.Api("https://example.test", "example.test", "", 50));

        GutenbergHarvestClient client = new GutenbergHarvestClient(properties);
        URI aleph = URI.create("https://aleph.gutenberg.org/1/0/0/8/10084/10084.zip");
        URI other = URI.create("https://example.org/archive.zip");

        assertTrue(client.isAlephHttps(aleph));
        assertFalse(client.isAlephHttps(other));
        assertEquals(
                "http://aleph.gutenberg.org/1/0/0/8/10084/10084.zip",
                client.toHttp(aleph).toString());
    }
}

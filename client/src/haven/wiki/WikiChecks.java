package haven.wiki;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic offline checks for wiki URL, parsing, cache, and rate-limit behavior. */
public final class WikiChecks {
    private int passed;

    public static void main(String[] args) throws Exception {
        new WikiChecks().run();
    }

    private void run() throws Exception {
        normalizationIsBounded();
        articleLinksAreEncoded();
        searchMarkupBecomesPlainText();
        wikiMarkupIsCleaned();
        apiResultsRetainProvenance();
        exactTitleRanksFirst();
        articleHtmlBecomesNativeContent();
        unsafeImagesAreRejected();
        cacheAvoidsDuplicateRequests();
        System.out.println("Wiki checks passed: " + passed + "/9");
    }

    private void normalizationIsBounded() {
        check("Boar combat".equals(RingOfBrodgarWikiService.normalizeQuery("  Boar   combat  ")),
                "query whitespace was not normalized");
        check(RingOfBrodgarWikiService.normalizeQuery("x".repeat(200)).length() == 120,
                "query length was not bounded");
        passed++;
    }

    private void articleLinksAreEncoded() {
        URI uri = RingOfBrodgarWikiService.articleUri("A #Test Page");
        check("https://ringofbrodgar.com/wiki/A_%23Test_Page".equals(uri.toASCIIString()),
                "article title was not safely encoded: " + uri);
        passed++;
    }

    private void searchMarkupBecomesPlainText() {
        String plain = RingOfBrodgarWikiService.stripSnippet(
                "Fight <span class=\"searchmatch\">Boar</span> &amp; survive<br>Safely");
        check("Fight Boar & survive Safely".equals(plain),
                "search HTML was not reduced to plain text: " + plain);
        passed++;
    }

    private void wikiMarkupIsCleaned() {
        String plain = RingOfBrodgarWikiService.cleanWikiMarkup(
                "{{GM|Craft|Food}} [[Raw Boar|boar meat]] [[File:Boar.png]] objectreq: [[Boar Tusk]]");
        check("boar meat Boar Tusk".equals(plain),
                "wiki templates leaked into result text: " + plain);
        passed++;
    }

    private void apiResultsRetainProvenance() {
        WikiSearchResponse response = RingOfBrodgarWikiService.parseSearchResponse("boar", sample(), 42L);
        check(response.results.size() == 1, "expected one parsed result");
        WikiSearchResult result = response.results.get(0);
        check(result.pageId == 14165 && result.wordCount == 893, "page metadata drifted");
        check("Boar".equals(result.title) && result.updatedAt != null, "page provenance was lost");
        check("https://ringofbrodgar.com/wiki/Boar".equals(result.articleUri.toASCIIString()),
                "canonical article link is incorrect");
        passed++;
    }

    private void exactTitleRanksFirst() {
        String json = "{\"query\":{\"search\":[" +
                "{\"title\":\"Boar Tusk\",\"pageid\":2,\"snippet\":\"tusk\"}," +
                "{\"title\":\"Raw Boar\",\"pageid\":3,\"snippet\":\"meat\"}," +
                "{\"title\":\"Boar\",\"pageid\":1,\"snippet\":\"animal\"}]}}";
        WikiSearchResponse response = RingOfBrodgarWikiService.parseSearchResponse("boar", json, 42L);
        check("Boar".equals(response.results.get(0).title),
                "exact title was not promoted ahead of partial matches");
        passed++;
    }

    private void articleHtmlBecomesNativeContent() {
        String json = "{\"parse\":{" +
                "\"title\":\"Boar\",\"displaytitle\":\"<span>Boar</span>\",\"revid\":122416," +
                "\"text\":\"<table class='infobox'><tr><th>Hitpoints</th><td>125</td></tr>" +
                "<tr><td><img src='/images/6/6f/Boar.png'></td></tr></table>" +
                "<h2>Combat</h2><p>A dangerous wild animal.</p><ul><li>Keep moving.</li></ul>\"}}";
        WikiArticle article = RingOfBrodgarWikiService.parseArticleResponse(json);
        check("Boar".equals(article.title) && article.revisionId == 122416,
                "article provenance was not retained");
        check(article.text.contains("Quick facts") && article.text.contains("Hitpoints: 125") &&
                        article.text.contains("Combat") && article.text.contains("dangerous wild animal"),
                "article HTML did not become readable native content: " + article.text);
        check(article.leadImageUri != null &&
                        "https://ringofbrodgar.com/images/6/6f/Boar.png".equals(
                                article.leadImageUri.toASCIIString()),
                "safe lead image was not extracted");
        passed++;
    }

    private void unsafeImagesAreRejected() {
        check(RingOfBrodgarWikiService.isSafeWikiImage(
                        URI.create("https://ringofbrodgar.com/images/a/a1/Boar.png")),
                "valid Ring of Brodgar image was rejected");
        check(!RingOfBrodgarWikiService.isSafeWikiImage(
                        URI.create("https://ringofbrodgar.com.evil.example/images/Boar.png")),
                "lookalike image host was accepted");
        check(!RingOfBrodgarWikiService.isSafeWikiImage(
                        URI.create("https://ringofbrodgar.com/wiki/Boar")),
                "non-image wiki path was accepted");
        passed++;
    }

    private void cacheAvoidsDuplicateRequests() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicInteger requests = new AtomicInteger();
        RingOfBrodgarWikiService service = new RingOfBrodgarWikiService(uri -> {
            requests.incrementAndGet();
            return(sample());
        }, clock::get, 60_000L);
        try {
            WikiSearchResponse first = service.search("Boar").get(2, TimeUnit.SECONDS);
            WikiSearchResponse cached = service.search(" boar ").get(2, TimeUnit.SECONDS);
            check(!first.cached && cached.cached && requests.get() == 1,
                    "duplicate query did not use the cache");
            try {
                service.search("Bear").get(2, TimeUnit.SECONDS);
                throw(new AssertionError("different query bypassed the crawl delay"));
            } catch(ExecutionException expected) {
                check(expected.getCause() instanceof RingOfBrodgarWikiService.RateLimitException,
                        "wrong rate-limit failure: " + expected.getCause());
            }
            clock.addAndGet(60_000L);
            service.search("Bear").get(2, TimeUnit.SECONDS);
            check(requests.get() == 2, "request did not resume after the crawl delay");
        } finally {
            service.close();
        }
        passed++;
    }

    private static String sample() {
        return("{\"query\":{\"search\":[{" +
                "\"ns\":0,\"title\":\"Boar\",\"pageid\":14165," +
                "\"wordcount\":893,\"snippet\":\"Fight <span>Boar</span>\"," +
                "\"timestamp\":\"2026-05-03T23:02:57Z\"}]}}");
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}

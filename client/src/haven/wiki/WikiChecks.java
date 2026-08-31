package haven.wiki;

import java.net.URI;
import java.util.List;
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
        searchRankingAndAliasesWork();
        categoryAndNoResultSearchWork();
        navigationPreservesBackAndForward();
        articleLinksAndCategoriesResolve();
        referencesRoundTripWithoutUsingDisplayIdentity();
        liveActionsUseRealMenuKinds();
        ornamentMotionHonorsReducedSetting();
        System.out.println("Wiki checks passed: " + passed + "/16");
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
                "<h2>Combat</h2><p>A dangerous wild animal.</p><ul><li>Keep moving.</li></ul>" +
                "<h2>Gallery</h2><ul class='gallery'><li><img src='/images/7/7a/Boar-Coat.png'></li>" +
                "<li><img src='/images/8/8b/Boar-Hide.png'></li></ul>\"}}";
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
        check(article.imageUris.size() == 3 &&
                        "https://ringofbrodgar.com/images/8/8b/Boar-Hide.png".equals(
                                article.imageUris.get(2).toASCIIString()),
                "article gallery images were not collected and deduplicated");
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

    private void searchRankingAndAliasesWork() {
        WikiSearchIndex index = new WikiSearchIndex();
        index.put(new WikiSearchIndex.Record(WikiReference.guide("Iron Ore", "Ores"),
                "Raw metallic resource", List.of("hematite")));
        index.put(new WikiSearchIndex.Record(WikiReference.guide("Iron", "Metals"),
                "Processed metal", List.of()));
        index.put(new WikiSearchIndex.Record(WikiReference.guide("Smelter", "Buildings"),
                "Processes ore", List.of("iron")));
        check("Iron".equals(index.search("iron", 10).get(0).reference.title),
                "exact local title did not rank first");
        check("Iron Ore".equals(index.search("hematite", 10).get(0).reference.title),
                "indexed alias did not resolve its record");
        check(index.search("ore", 10).stream().anyMatch(record ->
                        "Iron Ore".equals(record.reference.title)),
                "partial title search did not resolve");
        passed++;
    }

    private void categoryAndNoResultSearchWork() {
        WikiSearchIndex index = new WikiSearchIndex();
        index.put(new WikiSearchIndex.Record(WikiReference.guide("Boar", "Creatures"),
                "Wild animal", List.of()));
        check(index.search("creatures", 10).size() == 1,
                "category term did not find its record");
        check(index.search("definitely absent", 10).isEmpty(),
                "no-result search returned unrelated records");
        passed++;
    }

    private void navigationPreservesBackAndForward() {
        WikiNavigationState navigation = new WikiNavigationState();
        WikiReference ore = WikiReference.guide("Iron Ore", "Ores");
        WikiReference smelter = WikiReference.guide("Smelter", "Buildings");
        WikiReference ingot = WikiReference.guide("Iron Ingot", "Metals");
        navigation.open(ore);
        navigation.open(smelter);
        navigation.open(ingot);
        check(navigation.back().equals(smelter) && navigation.back().equals(ore),
                "back navigation lost its record chain");
        check(navigation.forward().equals(smelter) && navigation.forward().equals(ingot),
                "forward navigation did not restore the record chain");
        passed++;
    }

    private void articleLinksAndCategoriesResolve() {
        String json = "{\"parse\":{" +
                "\"title\":\"Iron Ingot\",\"displaytitle\":\"Iron Ingot\",\"revid\":7," +
                "\"text\":\"<p>Metal.</p>\"," +
                "\"categories\":[{\"category\":\"Metals\"}]," +
                "\"links\":[{\"ns\":0,\"title\":\"Iron Ore\"},{\"ns\":1,\"title\":\"Talk:Iron\"}]}}";
        WikiArticle article = RingOfBrodgarWikiService.parseArticleResponse(json);
        check(article.categories.equals(List.of("Metals")), "article category was not retained");
        check(article.links.size() == 1 && "Iron Ore".equals(article.links.get(0).title),
                "main-namespace article link did not become a stable reference");
        passed++;
    }

    private void referencesRoundTripWithoutUsingDisplayIdentity() {
        WikiReference original = WikiReference.action("paginae/craft/axe", "Stone Axe", "Crafting");
        WikiReference restored = WikiReference.decode(original.encode());
        check(original.equals(restored) && "paginae/craft/axe".equals(restored.resourceName),
                "stable action reference did not survive preference encoding");
        passed++;
    }

    private void liveActionsUseRealMenuKinds() {
        check("Crafting".equals(WikiGameDataAdapter.category(
                        "paginae/craft/stoneaxe", new String[] {"craft", "stoneaxe"})),
                "craft action was not categorized from authoritative action data");
        check("Buildings".equals(WikiGameDataAdapter.category(
                        "paginae/bld/smelter", new String[] {"bp", "smelter"})),
                "building action was not categorized from authoritative action data");
        check("Equipment".equals(WikiGameDataAdapter.category(
                        "paginae/equip/armor", new String[] {"use", "armor"})),
                "equipment action was not assigned to a type filter");
        check("Food".equals(WikiGameDataAdapter.category(
                        "paginae/food/stew", new String[] {"use", "stew"})),
                "food action was not assigned to a type filter");
        passed++;
    }

    private void ornamentMotionHonorsReducedSetting() {
        check(WikiOrnamentWidget.revealAfter(0.2, 0.25, false) > 0.2,
                "animated ornament reveal did not advance");
        check(WikiOrnamentWidget.revealAfter(0.2, 0.25, true) == 1.0,
                "reduced motion did not select the fully revealed static state");
        check(WikiOrnamentWidget.revealAfter(0.9, 2.0, false) == 1.0,
                "ornament reveal exceeded its bound");
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

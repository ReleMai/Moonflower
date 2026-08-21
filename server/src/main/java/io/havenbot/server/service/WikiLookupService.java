package io.havenbot.server.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WikiLookupService {
    private static final String BASE_URL = "https://ringofbrodgar.com";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration ICON_CACHE_TTL = Duration.ofDays(30);
    private static final Path ICON_CACHE_DIR = Path.of("..", "server-data", "wiki-icons");
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("\"url\":\"([^\"]+)\"");
    private static final Map<String, String> FILE_ICON_OVERRIDES = Map.ofEntries(
            Map.entry("stealth", "File:Icon stealthunder.png"),
            Map.entry("survival", "File:Wilderness Survival.png"),
            Map.entry("lore", "File:Plant Lore.png"),
            Map.entry("will", "File:The Will to Power.png"),
            Map.entry("marksmanship", "File:Legacy-Marksmanship.png"),
            Map.entry("carpentry", "File:Carpentry.png"),
            Map.entry("sewing", "File:Sewing.png"),
            Map.entry("cooking", "File:Cooking.png"),
            Map.entry("farming", "File:Farming.png"),
            Map.entry("mining", "File:Mining.png"),
            Map.entry("swimming", "File:Swimming.png"),
            Map.entry("unarmed combat", "File:Legacy-Unarmed Combat.png")
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, IconCacheEntry> iconCache = new ConcurrentHashMap<>();

    public WikiDetail lookup(String label, String kind, String wikiTitle, String wikiSection, String wikiUrl) {
        WikiDetail builtin = builtinDetail(label, kind, wikiUrl);
        if (builtin != null) {
            return builtin;
        }

        ResolvedTarget target = resolveTarget(label, kind, wikiTitle, wikiSection, wikiUrl);
        String cacheKey = target.pageTitle + "#" + target.sectionTitle;
        CacheEntry existing = cache.get(cacheKey);
        if (existing != null && existing.expiresAt().isAfter(Instant.now())) {
            return existing.detail();
        }

        WikiDetail detail;
        try {
            Document document = fetch(target.pageTitle);
            detail = target.sectionTitle.isBlank() ? parseArticle(target, document) : parseSection(target, document);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            detail = fallbackDetail(target, "The wiki page could not be loaded right now.");
        }
        cache.put(cacheKey, new CacheEntry(detail, Instant.now().plus(CACHE_TTL)));
        return detail;
    }

    public WikiIcon lookupIcon(String label, String kind, String wikiTitle, String wikiSection, String wikiUrl) {
        ResolvedTarget target = resolveTarget(label, kind, wikiTitle, wikiSection, wikiUrl);
        String cacheKey = normalizeCacheKey(target.pageTitle + "#" + target.sectionTitle + "#" + target.label + "#" + target.kind);
        IconCacheEntry existing = iconCache.get(cacheKey);
        if (existing != null && existing.expiresAt().isAfter(Instant.now()) && Files.exists(existing.path())) {
            return new WikiIcon(existing.path(), existing.contentType());
        }

        try {
            Files.createDirectories(ICON_CACHE_DIR);
            Document document = fetch(target.pageTitle);
            String remoteUrl = resolveIconUrl(target, document);
            if (remoteUrl.isBlank()) {
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(remoteUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "HavenBot/1.0 (+dashboard wiki icon cache)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400 || response.body().length == 0) {
                return null;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("image/png");
            Path path = ICON_CACHE_DIR.resolve(cacheKey + extensionFor(contentType));
            Files.write(path, response.body());
            iconCache.put(cacheKey, new IconCacheEntry(path, contentType, Instant.now().plus(ICON_CACHE_TTL)));
            return new WikiIcon(path, contentType);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private ResolvedTarget resolveTarget(String label, String kind, String wikiTitle, String wikiSection, String wikiUrl) {
        String normalizedLabel = label == null ? "" : label.trim();
        String normalizedKind = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (!wikiTitle.isBlank()) {
            return new ResolvedTarget(normalizedLabel, normalizedKind, wikiTitle, wikiSection == null ? "" : wikiSection.trim(), sourceUrl(wikiTitle, wikiSection));
        }
        return switch (normalizedKind) {
            case "attribute" -> new ResolvedTarget(normalizedLabel, normalizedKind, "Attributes", normalizedLabel, sourceUrl("Attributes", normalizedLabel));
            case "ability" -> new ResolvedTarget(normalizedLabel, normalizedKind, "Abilities", normalizedLabel, sourceUrl("Abilities", normalizedLabel));
            default -> new ResolvedTarget(normalizedLabel, normalizedKind, normalizedLabel, "", wikiUrl == null || wikiUrl.isBlank() ? sourceUrl(normalizedLabel, "") : wikiUrl);
        };
    }

    private Document fetch(String pageTitle) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl(pageTitle, "")))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "HavenBot/1.0 (+dashboard wiki lookup)")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("Wiki returned status " + response.statusCode());
        }
        return Jsoup.parse(response.body(), sourceUrl(pageTitle, ""));
    }

    private String resolveIconUrl(ResolvedTarget target, Document document) throws IOException, InterruptedException {
        Element content = contentRoot(document);
        if (content != null) {
            Element image = content.selectFirst("table.infobox img, .infobox img, .thumb img, img.mw-file-element");
            if (image != null) {
                String src = image.absUrl("src");
                if (!src.isBlank()) {
                    return src;
                }
            }
        }

        String override = FILE_ICON_OVERRIDES.get(normalizeLookupKey(target.label));
        if (override != null) {
            String url = imageUrlForFileTitle(override);
            if (!url.isBlank()) {
                return url;
            }
        }

        for (String fileTitle : searchFileTitleCandidates(target)) {
            String url = imageUrlForFileTitle(fileTitle);
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private List<String> searchFileTitleCandidates(ResolvedTarget target) {
        List<String> candidates = new ArrayList<>();
        String label = target.label == null ? "" : target.label.trim();
        if (!label.isBlank()) {
            candidates.add("File:" + label + ".png");
            candidates.add("File:" + label.replace(" ", "_") + ".png");
        }
        if (!target.pageTitle.isBlank() && !target.pageTitle.equals(label)) {
            candidates.add("File:" + target.pageTitle + ".png");
            candidates.add("File:" + target.pageTitle.replace(" ", "_") + ".png");
        }
        return candidates;
    }

    private String imageUrlForFileTitle(String fileTitle) throws IOException, InterruptedException {
        String apiUrl = BASE_URL + "/api.php?action=query&titles="
                + URLEncoder.encode(fileTitle, StandardCharsets.UTF_8)
                + "&prop=imageinfo&iiprop=url&format=json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "HavenBot/1.0 (+dashboard wiki icon cache)")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            return "";
        }
        Matcher matcher = IMAGE_URL_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("\\/", "/");
    }

    private String extensionFor(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return ".jpg";
        }
        if (normalized.contains("gif")) {
            return ".gif";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        if (normalized.contains("svg")) {
            return ".svg";
        }
        return ".png";
    }

    private String normalizeCacheKey(String value) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return encoded.length() > 120 ? encoded.substring(0, 120) : encoded;
    }

    private String normalizeLookupKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private WikiDetail parseSection(ResolvedTarget target, Document document) {
        Element content = contentRoot(document);
        if (content == null) {
            return fallbackDetail(target, "No wiki content was found for this section.");
        }
        Element headline = findHeadline(content, target.sectionTitle);
        if (headline == null) {
            return parseArticle(target, document);
        }

        Element heading = headline.parent();
        int level = headingLevel(heading);
        List<Element> nodes = new ArrayList<>();
        for (Element sibling = heading.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
            if (isHeading(sibling) && headingLevel(sibling) <= level) {
                break;
            }
            nodes.add(sibling);
        }

        List<String> prelude = target.pageTitle.equalsIgnoreCase("Abilities") ? preludeLines(content, 2) : List.of();
        List<String> bodyLines = extractLines(nodes, 18);
        List<String> formulas = new ArrayList<>();
        List<String> details = new ArrayList<>();
        List<String> unlocks = new ArrayList<>();

        for (String line : prelude) {
            if (looksLikeFormula(line)) {
                formulas.add(line);
            }
        }
        for (String line : bodyLines) {
            if (looksLikeUnlock(line)) {
                unlocks.add(line);
            } else if (looksLikeFormula(line)) {
                formulas.add(line);
            } else {
                details.add(line);
            }
        }

        List<WikiFact> facts = new ArrayList<>();
        if (target.pageTitle.equalsIgnoreCase("Abilities")) {
            facts.add(new WikiFact("Level-up Cost", "100 LP * (current level + 1)"));
        }

        List<WikiSection> sections = new ArrayList<>();
        if (!details.isEmpty()) {
            sections.add(new WikiSection("What It Does", details));
        }
        if (!unlocks.isEmpty()) {
            sections.add(new WikiSection("Unlocks And Uses", unlocks));
        }
        if (!formulas.isEmpty()) {
            sections.add(new WikiSection("Formulas", formulas));
        }

        return new WikiDetail(
                target.label,
                firstNonBlank(details, prelude, "No wiki summary available."),
                target.sourceUrl,
                facts,
                sections.isEmpty() ? List.of(new WikiSection("Overview", bodyLines.isEmpty() ? List.of("No wiki details available.") : bodyLines)) : sections
        );
    }

    private WikiDetail parseArticle(ResolvedTarget target, Document document) {
        Element content = contentRoot(document);
        if (content == null) {
            return fallbackDetail(target, "No wiki content was found for this page.");
        }

        List<WikiFact> facts = parseInfoboxFacts(content);
        List<WikiSection> sections = new ArrayList<>();
        List<String> intro = introductoryParagraphs(content, 3);
        List<String> formulas = new ArrayList<>();
        List<String> unlocks = new ArrayList<>();

        for (WikiFact fact : facts) {
            if (looksLikeUnlock(fact.label() + " " + fact.value())) {
                unlocks.add(fact.label() + ": " + fact.value());
            }
        }

        List<Element> sectionHeads = content.select("> h2, > h3");
        int added = 0;
        for (Element heading : sectionHeads) {
            if (added >= 4) {
                break;
            }
            String title = cleanText(heading.text());
            if (title.isBlank() || title.equalsIgnoreCase("Contents")) {
                continue;
            }
            List<String> lines = extractLines(collectSectionNodes(heading), 8);
            if (lines.isEmpty()) {
                continue;
            }
            sections.add(new WikiSection(title, lines));
            for (String line : lines) {
                if (looksLikeFormula(line)) {
                    formulas.add(line);
                }
            }
            added++;
        }

        if (!unlocks.isEmpty()) {
            sections.add(0, new WikiSection("Unlocks", unlocks));
        }
        if (!formulas.isEmpty()) {
            sections.add(new WikiSection("Formulas", dedupe(formulas, 8)));
        }

        String summary = !intro.isEmpty() ? intro.getFirst() : (!sections.isEmpty() && !sections.getFirst().lines().isEmpty() ? sections.getFirst().lines().getFirst() : "No wiki summary available.");
        return new WikiDetail(
                target.label,
                summary,
                target.sourceUrl,
                facts,
                sections.isEmpty() ? List.of(new WikiSection("Overview", intro.isEmpty() ? List.of("No wiki details available.") : intro)) : sections
        );
    }

    private List<Element> collectSectionNodes(Element heading) {
        int level = headingLevel(heading);
        List<Element> nodes = new ArrayList<>();
        for (Element sibling = heading.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
            if (isHeading(sibling) && headingLevel(sibling) <= level) {
                break;
            }
            nodes.add(sibling);
        }
        return nodes;
    }

    private List<WikiFact> parseInfoboxFacts(Element content) {
        Element infobox = content.selectFirst("table.infobox");
        if (infobox == null) {
            return List.of();
        }
        List<WikiFact> facts = new ArrayList<>();
        for (Element row : infobox.select("tr")) {
            Element label = row.selectFirst("th");
            Element value = row.selectFirst("td");
            if (label == null || value == null) {
                continue;
            }
            String factLabel = cleanText(label.text());
            String factValue = truncate(cleanText(value.text()), 220);
            if (factLabel.isBlank() || factValue.isBlank()) {
                continue;
            }
            facts.add(new WikiFact(factLabel, factValue));
            if (facts.size() >= 10) {
                break;
            }
        }
        return facts;
    }

    private List<String> introductoryParagraphs(Element content, int maxLines) {
        List<String> lines = new ArrayList<>();
        for (Element child : content.children()) {
            if (child.tagName().equals("div") && child.id().equals("toc")) {
                break;
            }
            if (child.tagName().equals("p")) {
                String text = cleanText(child.text());
                if (!text.isBlank()) {
                    lines.add(truncate(text, 320));
                }
            }
            if (lines.size() >= maxLines) {
                break;
            }
        }
        return lines;
    }

    private List<String> preludeLines(Element content, int maxLines) {
        return introductoryParagraphs(content, maxLines);
    }

    private List<String> extractLines(List<Element> nodes, int maxLines) {
        List<String> lines = new ArrayList<>();
        for (Element node : nodes) {
            if (lines.size() >= maxLines) {
                break;
            }
            switch (node.tagName()) {
                case "p", "dd" -> {
                    String text = cleanText(node.text());
                    if (!text.isBlank()) {
                        lines.add(truncate(text, 320));
                    }
                }
                case "ul", "ol" -> {
                    for (Element item : node.select("> li")) {
                        String text = cleanText(item.text());
                        if (!text.isBlank()) {
                            lines.add(truncate(text, 320));
                        }
                        if (lines.size() >= maxLines) {
                            break;
                        }
                    }
                }
                default -> {
                }
            }
        }
        return dedupe(lines, maxLines);
    }

    private Element findHeadline(Element content, String sectionTitle) {
        String normalized = normalize(sectionTitle);
        for (Element headline : content.select(".mw-headline")) {
            if (normalize(headline.text()).equals(normalized) || normalize(headline.id()).equals(normalized)) {
                return headline;
            }
        }
        return null;
    }

    private Element contentRoot(Document document) {
        return document.selectFirst("#mw-content-text .mw-parser-output");
    }

    private boolean isHeading(Element element) {
        return element != null && element.tagName().matches("h[1-6]");
    }

    private int headingLevel(Element element) {
        if (element == null || element.tagName().length() != 2) {
            return 6;
        }
        return Character.digit(element.tagName().charAt(1), 10);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('_', ' ').replaceAll("[^A-Za-z0-9 ]", "").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private boolean looksLikeFormula(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        return value.contains("softcap")
                || value.contains("hardcap")
                || value.contains("formula")
                || value.contains("sqrt")
                || value.contains("calculated")
                || value.contains("quality")
                || value.contains("lp *")
                || value.contains("damage")
                || value.contains("perception*")
                || value.contains("survival*")
                || value.contains("dexterity")
                || value.contains("smithing")
                || value.contains("toolq");
    }

    private boolean looksLikeUnlock(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        return value.contains("unlock")
                || value.contains("required")
                || value.contains("used to craft")
                || value.contains("enables")
                || value.contains("necessary for");
    }

    private List<String> dedupe(List<String> lines, int maxLines) {
        List<String> unique = new ArrayList<>();
        for (String line : lines) {
            if (!unique.contains(line)) {
                unique.add(line);
            }
            if (unique.size() >= maxLines) {
                break;
            }
        }
        return unique;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String firstNonBlank(List<String> primary, List<String> secondary, String fallback) {
        for (String line : primary) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        for (String line : secondary) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        return fallback;
    }

    private WikiDetail fallbackDetail(ResolvedTarget target, String summary) {
        return new WikiDetail(
                target.label.isBlank() ? target.pageTitle : target.label,
                summary,
                target.sourceUrl,
                List.of(),
                List.of(new WikiSection("Overview", List.of(summary)))
        );
    }

    private WikiDetail builtinDetail(String label, String kind, String wikiUrl) {
        if (!Objects.equals(kind, "meter")) {
            return null;
        }
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "health" -> new WikiDetail(
                    "Health",
                    "Represents your current well-being, including soft and hard health values.",
                    wikiUrl,
                    List.of(new WikiFact("Tracks", "SHP / HHP / MHP")),
                    List.of(
                            new WikiSection("What It Does", List.of(
                                    "Health measures immediate survivability and determines how much damage your character can take.",
                                    "Soft hit points recover faster, while hard hit points represent longer-term injury."
                            )),
                            new WikiSection("Formulas", List.of(
                                    "Current dashboard telemetry tracks SHP, HHP, and MHP directly from the in-game meter model."
                            ))
                    )
            );
            case "stamina" -> new WikiDetail(
                    "Stamina",
                    "Represents the character's current physical exertion reserve.",
                    wikiUrl,
                    List.of(new WikiFact("Used For", "Movement, work, combat actions")),
                    List.of(new WikiSection("What It Does", List.of(
                            "Stamina is consumed by travel, crafting actions, and many exertion-heavy activities.",
                            "Low stamina limits how long the character can keep acting without rest or replenishment."
                    )))
            );
            case "energy" -> new WikiDetail(
                    "Energy",
                    "Represents the character's nourishment reserve and long-term capacity to act.",
                    wikiUrl,
                    List.of(new WikiFact("Used For", "Study, hunger economy, sustained activity")),
                    List.of(new WikiSection("What It Does", List.of(
                            "Energy reflects how well-fed the character is and influences how long you can keep progressing efficiently.",
                            "It is one of the main high-level survival and efficiency readings worth watching across long sessions."
                    )))
            );
            default -> null;
        };
    }

    private String sourceUrl(String wikiTitle, String wikiSection) {
        String encodedTitle = URLEncoder.encode(wikiTitle.trim().replace(" ", "_"), StandardCharsets.UTF_8).replace("+", "_");
        if (wikiSection == null || wikiSection.isBlank()) {
            return BASE_URL + "/wiki/" + encodedTitle;
        }
        return BASE_URL + "/wiki/" + encodedTitle + "#" + URLEncoder.encode(wikiSection.trim().replace(" ", "_"), StandardCharsets.UTF_8).replace("+", "_");
    }

    public record WikiDetail(String title, String summary, String sourceUrl, List<WikiFact> facts, List<WikiSection> sections) {
    }

    public record WikiFact(String label, String value) {
    }

    public record WikiSection(String title, List<String> lines) {
    }

    private record CacheEntry(WikiDetail detail, Instant expiresAt) {
    }

    public record WikiIcon(Path path, String contentType) {
    }

    private record IconCacheEntry(Path path, String contentType, Instant expiresAt) {
    }

    private record ResolvedTarget(String label, String kind, String pageTitle, String sectionTitle, String sourceUrl) {
    }
}

package haven.wiki;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.AttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Read-only, rate-limited MediaWiki search client for Ring of Brodgar. */
public final class RingOfBrodgarWikiService implements AutoCloseable {
    public static final URI HOME_URI = URI.create("https://ringofbrodgar.com/");
    public static final URI COPYRIGHT_URI =
            URI.create("https://ringofbrodgar.com/wiki/Ring_of_Brodgar:Copyrights");
    private static final String API_BASE = "https://ringofbrodgar.com/api.php";
    private static final String USER_AGENT =
            "MoonFlowerWiki/0.1 (+https://github.com/ReleMai/Moonflower)";
    private static final long REQUEST_INTERVAL_MILLIS = 60_000L;
    private static final int RESULT_LIMIT = 12;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_000_000L;
    private static final int CACHE_SIZE = 32;
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    interface Fetcher {
        String fetch(URI uri) throws IOException;
    }

    public static final class RateLimitException extends RuntimeException {
        public final long remainingSeconds;

        private RateLimitException(long remainingMillis) {
            super("Ring of Brodgar requests are limited to one per minute.");
            this.remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        }
    }

    private final Object lock = new Object();
    private final ExecutorService executor;
    private final Fetcher fetcher;
    private final LongSupplier clock;
    private final long requestIntervalMillis;
    private final Map<String, WikiSearchResponse> cache =
            new LinkedHashMap<String, WikiSearchResponse>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, WikiSearchResponse> eldest) {
                    return(size() > CACHE_SIZE);
                }
            };
    private final Map<String, WikiArticle> articleCache =
            new LinkedHashMap<String, WikiArticle>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, WikiArticle> eldest) {
                    return(size() > CACHE_SIZE);
                }
            };
    private final Map<String, BufferedImage> imageCache =
            new LinkedHashMap<String, BufferedImage>(12, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    return(size() > 12);
                }
            };
    private long nextRequestAt;

    public RingOfBrodgarWikiService() {
        this(RingOfBrodgarWikiService::fetchJson, System::currentTimeMillis,
                REQUEST_INTERVAL_MILLIS);
    }

    RingOfBrodgarWikiService(Fetcher fetcher, LongSupplier clock, long requestIntervalMillis) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.requestIntervalMillis = requestIntervalMillis;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task,
                    "Ring of Brodgar wiki " + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return(thread);
        });
    }

    public Future<WikiSearchResponse> search(String text) {
        String query = normalizeQuery(text);
        if(query.length() < 2)
            return(failedFuture(new IllegalArgumentException("Enter at least two characters.")));
        String key = query.toLowerCase(Locale.ROOT);
        long now = clock.getAsLong();
        synchronized(lock) {
            WikiSearchResponse cached = cache.get(key);
            if(cached != null)
                return(CompletableFuture.completedFuture(new WikiSearchResponse(
                        cached.query, cached.results, true, cached.fetchedAt)));
            if(now < nextRequestAt)
                return(failedFuture(new RateLimitException(nextRequestAt - now)));
            // Reserve the interval before dispatch so two quick clicks cannot start two requests.
            nextRequestAt = now + requestIntervalMillis;
        }
        return(executor.submit(() -> {
            WikiSearchResponse response = parseSearchResponse(query,
                    fetcher.fetch(searchUri(query)), clock.getAsLong());
            synchronized(lock) {
                cache.put(key, response);
            }
            return(response);
        }));
    }

    /** Fetches one user-selected page. Page and image requests never crawl linked pages. */
    public Future<WikiArticle> article(String title) {
        String normalized = normalizeQuery(title);
        if(normalized.isEmpty())
            return(failedFuture(new IllegalArgumentException("Select an article first.")));
        String key = normalized.toLowerCase(Locale.ROOT);
        synchronized(lock) {
            WikiArticle cached = articleCache.get(key);
            if(cached != null)
                return(CompletableFuture.completedFuture(cached.asCached()));
        }
        return(executor.submit(() -> {
            WikiArticle article = parseArticleResponse(fetcher.fetch(articleApiUri(normalized)));
            synchronized(lock) {
                articleCache.put(key, article);
            }
            return(article);
        }));
    }

    public Future<BufferedImage> image(URI uri) {
        if(!isSafeWikiImage(uri))
            return(failedFuture(new IllegalArgumentException("Refused an unsafe wiki image URL.")));
        String key = uri.toASCIIString();
        synchronized(lock) {
            BufferedImage cached = imageCache.get(key);
            if(cached != null)
                return(CompletableFuture.completedFuture(cached));
        }
        return(executor.submit(() -> {
            BufferedImage image = fetchImage(uri);
            synchronized(lock) {
                imageCache.put(key, image);
            }
            return(image);
        }));
    }

    public long secondsUntilNextRequest() {
        synchronized(lock) {
            return(Math.max(0L, (nextRequestAt - clock.getAsLong() + 999L) / 1000L));
        }
    }

    static String normalizeQuery(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return(normalized.length() > 120 ? normalized.substring(0, 120) : normalized);
    }

    static URI searchUri(String query) {
        String encoded = URLEncoder.encode(normalizeQuery(query), StandardCharsets.UTF_8);
        return(URI.create(API_BASE + "?action=query&list=search&srnamespace=0&srlimit=" +
                RESULT_LIMIT + "&srenablerewrites=1&format=json&formatversion=2&srsearch=" + encoded));
    }

    static URI articleApiUri(String title) {
        String encoded = URLEncoder.encode(normalizeQuery(title), StandardCharsets.UTF_8);
        return(URI.create(API_BASE + "?action=parse&redirects=1&prop=text%7Cdisplaytitle%7Crevid%7Ccategories%7Clinks" +
                "&format=json&formatversion=2&page=" + encoded));
    }

    static URI articleUri(String title) {
        String path = "/wiki/" + normalizeQuery(title).replace(' ', '_');
        try {
            return(new URI("https", "ringofbrodgar.com", path, null));
        } catch(Exception failure) {
            throw(new IllegalArgumentException("Invalid wiki title", failure));
        }
    }

    static WikiSearchResponse parseSearchResponse(String query, String json, long fetchedAt) {
        JSONObject root = new JSONObject(json);
        JSONObject queryObject = root.optJSONObject("query");
        JSONArray found = queryObject == null ? null : queryObject.optJSONArray("search");
        List<WikiSearchResult> results = new ArrayList<>();
        if(found != null) {
            for(int index = 0; index < found.length(); index++) {
                JSONObject item = found.getJSONObject(index);
                String title = item.optString("title", "").trim();
                if(title.isEmpty())
                    continue;
                results.add(new WikiSearchResult(item.optInt("pageid", -1), title,
                        cleanWikiMarkup(stripSnippet(item.optString("snippet", ""))),
                        item.optInt("wordcount", 0), parseInstant(item.optString("timestamp", "")),
                        articleUri(title)));
            }
        }
        results.sort(Comparator.comparingInt(result -> titleRank(result.title, query)));
        return(new WikiSearchResponse(query, results, false, fetchedAt));
    }

    static WikiArticle parseArticleResponse(String json) {
        JSONObject root = new JSONObject(json);
        JSONObject error = root.optJSONObject("error");
        if(error != null)
            throw(new IllegalArgumentException(error.optString("info", "Wiki article was not found.")));
        JSONObject parsed = root.optJSONObject("parse");
        if(parsed == null)
            throw(new IllegalArgumentException("Ring of Brodgar returned no article content."));
        String title = cleanWikiMarkup(stripSnippet(parsed.optString("displaytitle",
                parsed.optString("title", "Article"))));
        String html = parsed.optString("text", "");
        ArticleHtmlParser reader = new ArticleHtmlParser();
        try {
            new ParserDelegator().parse(new StringReader(html), reader, true);
        } catch(IOException impossibleForStringReader) {
            throw(new IllegalArgumentException("Could not parse the wiki article.",
                    impossibleForStringReader));
        }
        URI articleUri = articleUri(parsed.optString("title", title));
        List<String> categories = parseCategories(parsed.optJSONArray("categories"));
        String primaryCategory = categories.isEmpty() ? "Community Archive" : categories.get(0);
        WikiReference reference = WikiReference.guide(title, primaryCategory, articleUri);
        List<WikiReference> links = parseLinks(parsed.optJSONArray("links"));
        return(new WikiArticle(reference, title, reader.text(), parsed.optLong("revid", -1L),
                articleUri, reader.leadImage(), false, categories, links, reader.images()));
    }

    private static List<String> parseCategories(JSONArray source) {
        List<String> categories = new ArrayList<>();
        if(source == null)
            return(categories);
        for(int index = 0; index < source.length() && categories.size() < 12; index++) {
            JSONObject item = source.optJSONObject(index);
            if(item == null)
                continue;
            String category = item.optString("category", item.optString("*", "")).trim();
            if(category.startsWith("Category:"))
                category = category.substring("Category:".length()).trim();
            if(!category.isBlank() && !category.toLowerCase(Locale.ROOT).contains("maintenance") &&
                    !categories.contains(category))
                categories.add(category);
        }
        return(categories);
    }

    private static List<WikiReference> parseLinks(JSONArray source) {
        List<WikiReference> links = new ArrayList<>();
        Map<String, WikiReference> unique = new LinkedHashMap<>();
        if(source == null)
            return(links);
        for(int index = 0; index < source.length() && links.size() < 80; index++) {
            JSONObject item = source.optJSONObject(index);
            if(item == null || item.optInt("ns", 0) != 0)
                continue;
            String title = item.optString("title", item.optString("*", "")).trim();
            if(title.isBlank() || title.contains(":"))
                continue;
            WikiReference link = WikiReference.guide(title, "Related Records");
            unique.putIfAbsent(link.articleUri.normalize().getPath().toLowerCase(Locale.ROOT), link);
            if(unique.size() >= 80)
                break;
        }
        links.addAll(unique.values());
        return(links);
    }

    static String stripSnippet(String html) {
        if(html == null || html.isBlank())
            return("");
        StringBuilder text = new StringBuilder();
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                @Override
                public void handleText(char[] data, int position) {
                    text.append(data).append(' ');
                }

                @Override
                public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                    if(tag == HTML.Tag.BR)
                        text.append(' ');
                }
            }, true);
        } catch(IOException impossibleForStringReader) {
            return(html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
        }
        return(text.toString().replaceAll("\\s+", " ").trim());
    }

    static String cleanWikiMarkup(String value) {
        String cleaned = value == null ? "" : value;
        cleaned = cleaned.replaceAll("(?is)\\[\\[(?:File|Image):.*?]]", " ");
        for(int pass = 0; pass < 4; pass++)
            cleaned = cleaned.replaceAll("(?s)\\{\\{[^{}]*}}", " ");
        cleaned = cleaned.replaceAll("\\[\\[([^]|]+)\\|([^]]+)]]", "$2");
        cleaned = cleaned.replaceAll("\\[\\[([^]]+)]]", "$1");
        cleaned = cleaned.replaceAll("(?i)\\b(?:objectreq|requires|GM):?", " ");
        cleaned = cleaned.replace("'''", "").replace("''", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return(cleaned.replaceAll("^(?:\\.\\.\\.|…)+\\s*", ""));
    }

    private static int titleRank(String title, String query) {
        String candidate = title.toLowerCase(Locale.ROOT);
        String wanted = normalizeQuery(query).toLowerCase(Locale.ROOT);
        if(candidate.equals(wanted))
            return(0);
        if(candidate.startsWith(wanted))
            return(1);
        if(candidate.contains(wanted))
            return(2);
        return(3);
    }

    static boolean isSafeWikiImage(URI uri) {
        return(uri != null && "https".equalsIgnoreCase(uri.getScheme()) &&
                "ringofbrodgar.com".equalsIgnoreCase(uri.getHost()) &&
                uri.getRawUserInfo() == null && uri.getPath() != null &&
                uri.getPath().startsWith("/images/"));
    }

    private static final class ArticleHtmlParser extends HTMLEditorKit.ParserCallback {
        private static final int MAX_ARTICLE_CHARS = 24_000;
        private final StringBuilder text = new StringBuilder();
        private final List<String> cells = new ArrayList<>();
        private final List<URI> images = new ArrayList<>();
        private StringBuilder cell;
        private boolean infobox;
        private int ignoredTableDepth;
        private int ignoredElementDepth;
        private URI leadImage;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if(ignoredElementDepth > 0) {
                ignoredElementDepth++;
                return;
            }
            if(shouldIgnore(attributes)) {
                ignoredElementDepth = 1;
                return;
            }
            if(ignoredTableDepth > 0) {
                if(tag == HTML.Tag.TABLE)
                    ignoredTableDepth++;
                return;
            }
            if(tag == HTML.Tag.TABLE) {
                String cssClass = attribute(attributes, HTML.Attribute.CLASS);
                if(cssClass.toLowerCase(Locale.ROOT).contains("infobox")) {
                    infobox = true;
                    append("## Quick facts\n");
                } else {
                    ignoredTableDepth = 1;
                }
                return;
            }
            if(infobox) {
                if(tag == HTML.Tag.TR)
                    cells.clear();
                else if(tag == HTML.Tag.TH || tag == HTML.Tag.TD)
                    cell = new StringBuilder();
                return;
            }
            if(tag == HTML.Tag.H2)
                paragraph("## ");
            else if(tag == HTML.Tag.H3 || tag == HTML.Tag.H4)
                paragraph("### ");
            else if(tag == HTML.Tag.P)
                paragraph("");
            else if(tag == HTML.Tag.LI)
                append("\n• ");
            else if(tag == HTML.Tag.DT)
                append("\n");
            else if(tag == HTML.Tag.DD)
                append(": ");
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if(tag == HTML.Tag.IMG) {
                String source = attribute(attributes, HTML.Attribute.SRC);
                if(!source.isBlank()) {
                    URI candidate = HOME_URI.resolve(source.startsWith("//") ? "https:" + source : source);
                    if(isSafeWikiImage(candidate) && !images.contains(candidate)) {
                        images.add(candidate);
                        if(leadImage == null)
                            leadImage = candidate;
                    }
                }
            }
            if(ignoredElementDepth > 0 || ignoredTableDepth > 0)
                return;
            if(tag == HTML.Tag.BR) {
                append("\n");
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            if(ignoredElementDepth > 0 || ignoredTableDepth > 0)
                return;
            String value = new String(data);
            if(infobox && cell != null)
                cell.append(value).append(' ');
            else if(!infobox)
                append(value);
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if(ignoredElementDepth > 0) {
                ignoredElementDepth--;
                return;
            }
            if(ignoredTableDepth > 0) {
                if(tag == HTML.Tag.TABLE)
                    ignoredTableDepth--;
                return;
            }
            if(infobox) {
                if(tag == HTML.Tag.TH || tag == HTML.Tag.TD) {
                    String value = cleanWikiMarkup(cell == null ? "" : cell.toString());
                    if(!value.isBlank())
                        cells.add(value);
                    cell = null;
                } else if(tag == HTML.Tag.TR) {
                    flushFactRow();
                } else if(tag == HTML.Tag.TABLE) {
                    flushFactRow();
                    infobox = false;
                    append("\n");
                }
                return;
            }
            if(tag == HTML.Tag.P || tag == HTML.Tag.H2 || tag == HTML.Tag.H3 ||
                    tag == HTML.Tag.H4 || tag == HTML.Tag.LI || tag == HTML.Tag.DD)
                append("\n");
        }

        private void flushFactRow() {
            if(cells.size() >= 2) {
                String label = cells.get(0);
                String value = String.join(" • ", cells.subList(1, cells.size()));
                if(!label.equalsIgnoreCase(value))
                    append("• " + label + ": " + value + "\n");
            }
            cells.clear();
        }

        private void paragraph(String prefix) {
            if(text.length() > 0 && text.charAt(text.length() - 1) != '\n')
                append("\n");
            append("\n" + prefix);
        }

        private void append(String value) {
            if(text.length() >= MAX_ARTICLE_CHARS || value == null)
                return;
            int remaining = MAX_ARTICLE_CHARS - text.length();
            text.append(value, 0, Math.min(value.length(), remaining));
        }

        String text() {
            StringBuilder cleaned = new StringBuilder();
            int blankLines = 0;
            for(String line : text.toString().replace('\u00a0', ' ').split("\\R")) {
                String normalized = cleanWikiMarkup(line);
                if(normalized.isBlank()) {
                    blankLines++;
                    if(blankLines <= 1)
                        cleaned.append('\n');
                } else {
                    blankLines = 0;
                    cleaned.append(normalized).append('\n');
                }
            }
            return(cleaned.toString().trim());
        }

        URI leadImage() {
            return(leadImage);
        }

        List<URI> images() {
            return(new ArrayList<>(images));
        }

        private static boolean shouldIgnore(AttributeSet attributes) {
            String cssClass = attribute(attributes, HTML.Attribute.CLASS).toLowerCase(Locale.ROOT);
            return(cssClass.contains("mw-editsection") || cssClass.contains("navbox") ||
                    cssClass.contains("toc") || cssClass.contains("smw-highlighter") ||
                    cssClass.contains("noprint") || cssClass.contains("mw-collapsible"));
        }

        private static String attribute(AttributeSet attributes, Object name) {
            Object value = attributes == null ? null : attributes.getAttribute(name);
            return(value == null ? "" : value.toString());
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return(Instant.parse(value));
        } catch(DateTimeParseException failure) {
            return(null);
        }
    }

    private static BufferedImage fetchImage(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/*;q=0.8");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setInstanceFollowRedirects(true);
        try {
            int status = connection.getResponseCode();
            if(status < 200 || status >= 300)
                throw(new IOException("Wiki image returned HTTP " + status + '.'));
            byte[] bytes = readLimited(connection.getInputStream(), MAX_IMAGE_BYTES,
                    "Wiki image exceeded 5 MiB.");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if(image == null)
                throw(new IOException("Wiki image format is not supported."));
            if((long)image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS)
                throw(new IOException("Wiki image dimensions are too large."));
            return(image);
        } finally {
            connection.disconnect();
        }
    }

    private static String fetchJson(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setInstanceFollowRedirects(true);
        try {
            int status = connection.getResponseCode();
            if(status < 200 || status >= 300)
                throw(new IOException("Ring of Brodgar returned HTTP " + status + '.'));
            return(new String(readLimited(connection.getInputStream(), MAX_RESPONSE_BYTES,
                    "Ring of Brodgar response exceeded 1 MiB."), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input, int limit, String failure) throws IOException {
        try(input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for(int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if(total > limit)
                    throw(new IOException(failure));
                output.write(buffer, 0, read);
            }
            return(output.toByteArray());
        }
    }

    private static <T> Future<T> failedFuture(Throwable failure) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(failure);
        return(result);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}

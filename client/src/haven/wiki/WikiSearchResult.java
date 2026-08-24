package haven.wiki;

import java.net.URI;
import java.time.Instant;

/** One read-only Ring of Brodgar search result. */
public final class WikiSearchResult {
    public final int pageId;
    public final String title;
    public final String snippet;
    public final int wordCount;
    public final Instant updatedAt;
    public final URI articleUri;

    public WikiSearchResult(int pageId, String title, String snippet, int wordCount,
                            Instant updatedAt, URI articleUri) {
        this.pageId = pageId;
        this.title = title;
        this.snippet = snippet;
        this.wordCount = wordCount;
        this.updatedAt = updatedAt;
        this.articleUri = articleUri;
    }
}

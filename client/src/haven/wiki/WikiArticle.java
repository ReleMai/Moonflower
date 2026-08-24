package haven.wiki;

import java.net.URI;

/** Sanitized, read-only article content rendered by MoonFlower's native widgets. */
public final class WikiArticle {
    public final String title;
    public final String text;
    public final long revisionId;
    public final URI articleUri;
    public final URI leadImageUri;
    public final boolean cached;

    public WikiArticle(String title, String text, long revisionId, URI articleUri,
                       URI leadImageUri, boolean cached) {
        this.title = title;
        this.text = text;
        this.revisionId = revisionId;
        this.articleUri = articleUri;
        this.leadImageUri = leadImageUri;
        this.cached = cached;
    }

    public WikiArticle asCached() {
        return(cached ? this : new WikiArticle(title, text, revisionId, articleUri,
                leadImageUri, true));
    }
}

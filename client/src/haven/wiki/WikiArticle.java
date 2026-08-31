package haven.wiki;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sanitized, read-only article content rendered by MoonFlower's native widgets. */
public final class WikiArticle {
    public final WikiReference reference;
    public final String title;
    public final String text;
    public final long revisionId;
    public final URI articleUri;
    public final URI leadImageUri;
    /** Safe image URLs discovered in the article, including gallery images. */
    public final List<URI> imageUris;
    public final boolean cached;
    public final List<String> categories;
    public final List<WikiReference> links;

    public WikiArticle(String title, String text, long revisionId, URI articleUri,
                       URI leadImageUri, boolean cached) {
        this(WikiReference.guide(title, "Community Archive", articleUri), title, text,
                revisionId, articleUri, leadImageUri, cached,
                Collections.emptyList(), Collections.emptyList(),
                leadImageUri == null ? Collections.emptyList() : Collections.singletonList(leadImageUri));
    }

    public WikiArticle(WikiReference reference, String title, String text, long revisionId,
                       URI articleUri, URI leadImageUri, boolean cached,
                       List<String> categories, List<WikiReference> links) {
        this(reference, title, text, revisionId, articleUri, leadImageUri, cached,
                categories, links, leadImageUri == null ? Collections.emptyList() :
                        Collections.singletonList(leadImageUri));
    }

    public WikiArticle(WikiReference reference, String title, String text, long revisionId,
                       URI articleUri, URI leadImageUri, boolean cached,
                       List<String> categories, List<WikiReference> links, List<URI> imageUris) {
        this.reference = reference;
        this.title = title;
        this.text = text;
        this.revisionId = revisionId;
        this.articleUri = articleUri;
        this.leadImageUri = leadImageUri;
        this.cached = cached;
        this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
        this.links = Collections.unmodifiableList(new ArrayList<>(links));
        this.imageUris = Collections.unmodifiableList(new ArrayList<>(imageUris));
    }

    public WikiArticle asCached() {
        return(cached ? this : new WikiArticle(reference, title, text, revisionId,
                articleUri, leadImageUri, true, categories, links, imageUris));
    }
}

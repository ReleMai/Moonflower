package haven.wiki;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Stable target for a Codex record. Display text is never used as identity. */
public final class WikiReference {
    public enum Provenance {
        GUIDE,
        LIVE
    }

    public final String id;
    public final String title;
    public final String category;
    public final Provenance provenance;
    public final URI articleUri;
    public final String resourceName;

    private WikiReference(String id, String title, String category, Provenance provenance,
                          URI articleUri, String resourceName) {
        this.id = Objects.requireNonNull(id);
        this.title = title == null || title.isBlank() ? "Unknown record" : title.trim();
        this.category = category == null || category.isBlank() ? "Unsorted" : category.trim();
        this.provenance = Objects.requireNonNull(provenance);
        this.articleUri = articleUri;
        this.resourceName = resourceName;
    }

    public static WikiReference guide(String title, String category) {
        String normalized = RingOfBrodgarWikiService.normalizeQuery(title);
        return(new WikiReference("guide:" + slug(normalized), normalized, category,
                Provenance.GUIDE, RingOfBrodgarWikiService.articleUri(normalized), null));
    }

    public static WikiReference guide(String title, String category, URI uri) {
        String normalized = RingOfBrodgarWikiService.normalizeQuery(title);
        return(new WikiReference("guide:" + slug(normalized), normalized, category,
                Provenance.GUIDE, uri, null));
    }

    public static WikiReference action(String resourceName, String title, String category) {
        return(new WikiReference("action:" + resourceName, title, category,
                Provenance.LIVE, null, resourceName));
    }

    public static WikiReference decode(String encoded) {
        if(encoded == null || encoded.isBlank())
            return(null);
        String[] parts = encoded.split("\\t", -1);
        if(parts.length < 5)
            return(null);
        try {
            Provenance provenance = Provenance.valueOf(parts[0]);
            if(provenance == Provenance.LIVE)
                return(action(parts[4], parts[2], parts[3]));
            return(new WikiReference(parts[1], parts[2], parts[3], provenance,
                    RingOfBrodgarWikiService.articleUri(parts[2]), null));
        } catch(RuntimeException malformed) {
            return(null);
        }
    }

    public String encode() {
        return(provenance.name() + '\t' + id + '\t' + clean(title) + '\t' + clean(category) +
                '\t' + clean(resourceName));
    }

    private static String clean(String value) {
        return(value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '));
    }

    private static String slug(String value) {
        return(value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", ""));
    }

    @Override
    public boolean equals(Object other) {
        return(other instanceof WikiReference && id.equals(((WikiReference)other).id));
    }

    @Override
    public int hashCode() {
        return(id.hashCode());
    }

    @Override
    public String toString() {
        return(title);
    }
}

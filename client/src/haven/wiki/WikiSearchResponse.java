package haven.wiki;

import java.util.Collections;
import java.util.List;

/** Results plus enough provenance to explain cache and rate-limit behavior in the UI. */
public final class WikiSearchResponse {
    public final String query;
    public final List<WikiSearchResult> results;
    public final boolean cached;
    public final long fetchedAt;

    public WikiSearchResponse(String query, List<WikiSearchResult> results,
                              boolean cached, long fetchedAt) {
        this.query = query;
        this.results = Collections.unmodifiableList(results);
        this.cached = cached;
        this.fetchedAt = fetchedAt;
    }
}

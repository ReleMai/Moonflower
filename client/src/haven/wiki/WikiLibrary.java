package haven.wiki;

import haven.Utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small preference-backed player library; it does not alter Haven save data. */
public final class WikiLibrary {
    private static final int RECENT_LIMIT = 16;
    private static final int SEARCH_LIMIT = 20;
    private final String bookmarkKey;
    private final String recentKey;
    private final String searchKey;
    private final List<WikiReference> bookmarks = new ArrayList<>();
    private final List<WikiReference> recent = new ArrayList<>();
    private final List<String> searches = new ArrayList<>();

    public WikiLibrary(String characterId) {
        String suffix = characterId == null || characterId.isBlank() ? "global" : characterId;
        bookmarkKey = "moonflower-codex-bookmarks@" + suffix;
        recentKey = "moonflower-codex-recent@" + suffix;
        searchKey = "moonflower-codex-searches@" + suffix;
        decodeReferences(Utils.getprefsl(bookmarkKey, new String[0]), bookmarks);
        decodeReferences(Utils.getprefsl(recentKey, new String[0]), recent);
        searches.addAll(Utils.getprefsl(searchKey, new String[0]));
    }

    public List<WikiReference> bookmarks() {
        return(new ArrayList<>(bookmarks));
    }

    public List<WikiReference> recent() {
        return(new ArrayList<>(recent));
    }

    public List<String> searches() {
        return(new ArrayList<>(searches));
    }

    public boolean bookmarked(WikiReference reference) {
        return(reference != null && bookmarks.contains(reference));
    }

    public boolean toggleBookmark(WikiReference reference) {
        if(reference == null)
            return(false);
        if(bookmarks.remove(reference)) {
            persist(bookmarkKey, bookmarks);
            return(false);
        }
        bookmarks.add(0, reference);
        persist(bookmarkKey, bookmarks);
        return(true);
    }

    public void viewed(WikiReference reference) {
        if(reference == null)
            return;
        recent.remove(reference);
        recent.add(0, reference);
        trim(recent, RECENT_LIMIT);
        persist(recentKey, recent);
    }

    public void searched(String query) {
        String value = RingOfBrodgarWikiService.normalizeQuery(query);
        if(value.isBlank())
            return;
        searches.removeIf(existing -> existing.equalsIgnoreCase(value));
        searches.add(0, value);
        trim(searches, SEARCH_LIMIT);
        Utils.setprefsl(searchKey, searches);
    }

    private static void decodeReferences(List<String> encoded, List<WikiReference> target) {
        if(encoded == null)
            return;
        Set<WikiReference> unique = new LinkedHashSet<>();
        for(String value : encoded) {
            WikiReference reference = WikiReference.decode(value);
            if(reference != null)
                unique.add(reference);
        }
        target.addAll(unique);
    }

    private static void persist(String key, List<WikiReference> references) {
        List<String> encoded = new ArrayList<>();
        for(WikiReference reference : references)
            encoded.add(reference.encode());
        Utils.setprefsl(key, encoded);
    }

    private static <T> void trim(List<T> values, int limit) {
        while(values.size() > limit)
            values.remove(values.size() - 1);
    }
}

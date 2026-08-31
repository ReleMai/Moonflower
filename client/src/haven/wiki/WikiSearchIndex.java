package haven.wiki;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Incremental in-memory index for instant search over known Codex records. */
public final class WikiSearchIndex {
    public static final class Record {
        public final WikiReference reference;
        public final String summary;
        private final String searchable;

        public Record(WikiReference reference, String summary, Collection<String> keywords) {
            this.reference = reference;
            this.summary = summary == null ? "" : summary.trim();
            StringBuilder terms = new StringBuilder(reference.title).append(' ')
                    .append(reference.category).append(' ').append(this.summary);
            if(keywords != null) {
                for(String keyword : keywords)
                    terms.append(' ').append(keyword);
            }
            this.searchable = normalize(terms.toString());
        }
    }

    private final Map<String, Record> records = new LinkedHashMap<>();

    public synchronized void put(Record record) {
        if(record != null && record.reference != null)
            records.put(keyFor(record.reference), record);
    }

    public synchronized void putAll(Collection<Record> additions) {
        if(additions != null) {
            for(Record record : additions)
                put(record);
        }
    }

    public synchronized Record get(WikiReference reference) {
        return(reference == null ? null : records.get(keyFor(reference)));
    }

    public synchronized List<Record> all() {
        return(new ArrayList<>(records.values()));
    }

    public synchronized List<Record> category(String category, int limit) {
        List<Record> found = new ArrayList<>();
        for(Record record : records.values()) {
            if(record.reference.category.equalsIgnoreCase(category))
                found.add(record);
        }
        found.sort(Comparator.comparing(record -> record.reference.title.toLowerCase(Locale.ROOT)));
        return(bound(found, limit));
    }

    public synchronized List<String> categories() {
        List<String> categories = new ArrayList<>();
        for(Record record : records.values()) {
            if(!categories.contains(record.reference.category))
                categories.add(record.reference.category);
        }
        categories.sort(String.CASE_INSENSITIVE_ORDER);
        return(categories);
    }

    public synchronized List<Record> search(String query, int limit) {
        String wanted = normalize(query);
        if(wanted.isBlank())
            return(bound(all(), limit));
        String[] terms = wanted.split(" ");
        List<Record> found = new ArrayList<>();
        for(Record record : records.values()) {
            boolean matches = true;
            for(String term : terms) {
                if(!record.searchable.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if(matches)
                found.add(record);
        }
        found.sort(Comparator.comparingInt((Record record) -> rank(record, wanted))
                .thenComparing(record -> record.reference.title, String.CASE_INSENSITIVE_ORDER));
        return(bound(found, limit));
    }

    private static int rank(Record record, String query) {
        String title = normalize(record.reference.title);
        if(title.equals(query))
            return(0);
        if(title.startsWith(query))
            return(10);
        if(title.contains(query))
            return(20);
        String category = normalize(record.reference.category);
        if(category.equals(query))
            return(30);
        if(category.contains(query))
            return(40);
        return(50);
    }

    private static <T> List<T> bound(List<T> values, int limit) {
        int end = limit <= 0 ? values.size() : Math.min(values.size(), limit);
        return(new ArrayList<>(values.subList(0, end)));
    }

    private static String normalize(String value) {
        return(value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " "));
    }

    /** Guide aliases and redirects that resolve to one page share one shelf entry. */
    private static String keyFor(WikiReference reference) {
        if(reference.provenance == WikiReference.Provenance.GUIDE && reference.articleUri != null)
            return("guide-page:" + reference.articleUri.normalize().getPath().toLowerCase(Locale.ROOT));
        return(reference.provenance.name().toLowerCase(Locale.ROOT) + ':' + reference.id);
    }
}

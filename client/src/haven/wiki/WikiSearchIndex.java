package haven.wiki;

import java.text.Normalizer;
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
        private final String titleSearchable;
        private final String categorySearchable;
        private final String summarySearchable;
        private final String keywordSearchable;

        public Record(WikiReference reference, String summary, Collection<String> keywords) {
            this.reference = reference;
            this.summary = summary == null ? "" : summary.trim();
            this.titleSearchable = normalize(reference.title);
            this.categorySearchable = normalize(reference.category);
            this.summarySearchable = normalize(this.summary);
            StringBuilder terms = new StringBuilder();
            if(keywords != null) {
                for(String keyword : keywords)
                    terms.append(' ').append(keyword);
            }
            this.keywordSearchable = normalize(terms.toString());
        }
    }

    private static final int NO_MATCH = Integer.MAX_VALUE;

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
            if(score(record, wanted, terms) != NO_MATCH)
                found.add(record);
        }
        found.sort(Comparator.comparingInt((Record record) -> score(record, wanted, terms))
                .thenComparing(record -> record.reference.title, String.CASE_INSENSITIVE_ORDER));
        return(bound(found, limit));
    }

    /**
     * Scores exact words first, then prefixes, partial words, and safe short
     * typo matches. This keeps local typing forgiving without making a two
     * character query return unrelated records.
     */
    private static int score(Record record, String query, String[] terms) {
        int score = rank(record, query) * 100;
        for(String term : terms) {
            int best = Math.min(Math.min(fieldScore(term, record.titleSearchable, 0),
                            fieldScore(term, record.keywordSearchable, 35)),
                    Math.min(fieldScore(term, record.categorySearchable, 55),
                            fieldScore(term, record.summarySearchable, 75)));
            if(best == NO_MATCH)
                return(NO_MATCH);
            score += best;
        }
        return(score);
    }

    private static int fieldScore(String term, String field, int base) {
        if(field.isBlank())
            return(NO_MATCH);
        int best = NO_MATCH;
        for(String word : field.split(" ")) {
            if(word.equals(term))
                best = Math.min(best, base);
            else if(word.startsWith(term))
                best = Math.min(best, base + 6);
            else if(word.contains(term))
                best = Math.min(best, base + 12);
            else if(term.length() >= 4) {
                int distance = editDistance(term, word, 2);
                if(distance <= 2)
                    best = Math.min(best, base + 22 + (distance * 3));
            }
        }
        return(best);
    }

    private static int editDistance(String left, String right, int limit) {
        if(Math.abs(left.length() - right.length()) > limit)
            return(limit + 1);
        int[] previous = new int[right.length() + 1];
        for(int index = 0; index <= right.length(); index++)
            previous[index] = index;
        for(int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            int minimum = current[0];
            for(int column = 1; column <= right.length(); column++) {
                current[column] = Math.min(Math.min(current[column - 1] + 1,
                                previous[column] + 1),
                        previous[column - 1] + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1));
                minimum = Math.min(minimum, current[column]);
            }
            if(minimum > limit)
                return(limit + 1);
            previous = current;
        }
        return(previous[right.length()]);
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
        if(value == null)
            return("");
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return(withoutMarks.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " "));
    }

    /** Guide aliases and redirects that resolve to one page share one shelf entry. */
    private static String keyFor(WikiReference reference) {
        if(reference.provenance == WikiReference.Provenance.GUIDE && reference.articleUri != null)
            return("guide-page:" + reference.articleUri.normalize().getPath().toLowerCase(Locale.ROOT));
        return(reference.provenance.name().toLowerCase(Locale.ROOT) + ':' + reference.id);
    }
}

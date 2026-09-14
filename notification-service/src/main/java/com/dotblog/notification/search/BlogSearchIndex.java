package com.dotblog.notification.search;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory materialized view of published blogs, fed by Kafka.
 * Restart clears the index (toy exercise — production would use Elasticsearch/OpenSearch).
 */
@Component
public class BlogSearchIndex {

    public record Entry(String blogId, String title, String category) {}

    private final Map<String, Entry> byId = new ConcurrentHashMap<>();

    public void upsert(String blogId, String title, String category) {
        byId.put(blogId, new Entry(blogId, title, category));
    }

    public List<Entry> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String needle = q.toLowerCase(Locale.ROOT);
        return byId.values().stream()
                .filter(e -> contains(e.title(), needle) || contains(e.category(), needle))
                .collect(Collectors.toList());
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}

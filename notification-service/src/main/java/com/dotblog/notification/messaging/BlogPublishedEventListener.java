package com.dotblog.notification.messaging;

import com.dotblog.events.BlogPublishedEvent;
import com.dotblog.notification.search.BlogSearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BlogPublishedEventListener {

    private static final Logger log = LoggerFactory.getLogger(BlogPublishedEventListener.class);

    private final BlogSearchIndex index;

    public BlogPublishedEventListener(BlogSearchIndex index) {
        this.index = index;
    }

    @KafkaListener(
            topics = "${dotblog.kafka.topics.blog-published}",
            groupId = "notification-service-search",
            properties = {
                    "spring.json.value.default.type:com.dotblog.events.BlogPublishedEvent",
                    "spring.json.use.type.headers:false"
            }
    )
    public void onBlogPublished(BlogPublishedEvent event) {
        index.upsert(event.blogId(), event.title(), event.category());
        log.info("search index upserted blogId={} title={}", event.blogId(), event.title());
    }
}

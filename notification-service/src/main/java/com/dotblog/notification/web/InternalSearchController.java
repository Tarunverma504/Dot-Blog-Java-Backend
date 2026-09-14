package com.dotblog.notification.web;

import com.dotblog.notification.search.BlogSearchIndex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InternalSearchController {

    private final BlogSearchIndex index;

    public InternalSearchController(BlogSearchIndex index) {
        this.index = index;
    }

    @GetMapping("/internal/search")
    public List<BlogSearchIndex.Entry> search(@RequestParam("q") String q) {
        return index.search(q);
    }
}
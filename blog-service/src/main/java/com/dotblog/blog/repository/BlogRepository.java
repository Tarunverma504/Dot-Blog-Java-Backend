package com.dotblog.blog.repository;

import com.dotblog.blog.domain.Blog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BlogRepository extends MongoRepository<Blog, String> {
}

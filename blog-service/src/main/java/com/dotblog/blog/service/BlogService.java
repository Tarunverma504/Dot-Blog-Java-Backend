package com.dotblog.blog.service;

import com.dotblog.blog.domain.Blog;
import com.dotblog.blog.messaging.BlogEventPublisher;
import com.dotblog.blog.repository.BlogRepository;
import com.dotblog.blog.web.dto.BlogDetailResponse;
import com.dotblog.blog.web.dto.BlogListItem;
import com.dotblog.blog.web.dto.CreateBlogRequest;
import com.dotblog.blog.web.dto.UpdateBlogRequest;
import com.dotblog.blog.web.dto.UserBlogsResponse;
import com.dotblog.events.BlogPublishedEvent;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class BlogService {

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private final BlogRepository blogRepository;
    private final MongoTemplate mongoTemplate;
    private final UserClient userClient;
    private final MediaClient mediaClient;
    private final BlogEventPublisher blogEventPublisher;

    public BlogService(BlogRepository blogRepository,
                       MongoTemplate mongoTemplate,
                       UserClient userClient,
                       MediaClient mediaClient,
                       BlogEventPublisher blogEventPublisher) {
        this.blogRepository = blogRepository;
        this.mongoTemplate = mongoTemplate;
        this.userClient = userClient;
        this.mediaClient = mediaClient;
        this.blogEventPublisher = blogEventPublisher;
    }

    // ---------------- create / get / update (Day 4) ----------------

    public void createBlog(String userId, CreateBlogRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing body");
        }
        Blog blog = new Blog();
        blog.setUserId(userId);
        blog.setThumbnail(req.url());
        blog.setThumbnailPublicId(req.publicId());
        blog.setTitle(req.heading());
        blog.setBody(normalizeContent(req.content()));
        blog.setCategory(req.category());
        blog.setSubText(req.subText());
        Blog saved = blogRepository.save(blog);
        try {
            userClient.appendPost(userId, saved.getId());
        } catch (Exception e) {
            blogRepository.deleteById(saved.getId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    public BlogDetailResponse getBlog(String id, String optionalUserId) {
        Blog blog = blogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found"));
        if (blog.isHidden()) {
            // Soft-deleted: treat as gone for the entire world, including the owner.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found");
        }

        UserClient.AuthorView author = userClient.getAuthor(blog.getUserId()).orElse(null);
        String authorName = author != null && author.name() != null ? author.name() : "";
        String authorPhoto = author != null && author.profilePhoto() != null ? author.profilePhoto() : "";

        boolean isAlreadyLiked = optionalUserId != null && blog.getLikes() != null
                && blog.getLikes().stream().anyMatch(l -> optionalUserId.equals(l.getUserId()));

        List<Blog.Comment> comments = blog.getComments() != null ? new ArrayList<>(blog.getComments()) : new ArrayList<>();
        comments.sort(BlogService::compareByCreatedAt);
        List<Object> populatedComments = populateComments(comments);

        return new BlogDetailResponse(
                blog.getTitle(),
                blog.getBody(),
                blog.getThumbnail(),
                authorName,
                blog.getUserId(),
                authorPhoto,
                blog.getSubText(),
                formatCreatedAt(blog.getCreatedAt() != null
                        ? blog.getCreatedAt().atZone(ZoneOffset.UTC)
                        : ZonedDateTime.now(ZoneOffset.UTC)),
                blog.getLikes() != null ? blog.getLikes().size() : 0,
                populatedComments,
                isAlreadyLiked,
                blog.getCategory()
        );
    }

    public void updateBlog(String id, UpdateBlogRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing body");
        }
        Blog existing = blogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found"));

        boolean thumbUpdated = Boolean.TRUE.equals(req.isThumbnailUpdated());

        Update update = new Update()
                .set("Title", req.heading() != null ? req.heading() : existing.getTitle())
                .set("SubText", req.subText() != null ? req.subText() : existing.getSubText())
                .set("Body", normalizeContent(req.content()));

        String prevPublicIdToDelete = null;
        if (thumbUpdated) {
            update.set("Thumbnail", req.url() != null ? req.url() : "");
            update.set("Thumbnail_PublicId", req.publicId() != null ? req.publicId() : "");
            // Old asset becomes orphaned in Cloudinary — schedule a delete after the
            // DB write succeeds. We compare ids in case the client resent the same one.
            String prevId = existing.getThumbnailPublicId();
            if (prevId != null && !prevId.isBlank() && !prevId.equals(req.publicId())) {
                prevPublicIdToDelete = prevId;
            }
        } else if (req.category() != null) {
            update.set("Category", req.category());
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(id)),
                update,
                Blog.class
        );

        if (prevPublicIdToDelete != null) {
            mediaClient.delete(prevPublicIdToDelete);
        }
    }

    /**
     * Soft-delete: hides a blog from every read endpoint. Owner-only.
     *
     * <p>Idempotent: deleting an already-hidden blog is a no-op (200 OK). We
     * intentionally don't drop the Cloudinary thumbnail or the document — that
     * lets us restore via DB if a user changes their mind. A hard purge can be
     * an admin-only background job later.
     *
     * @throws ResponseStatusException 404 when the blog doesn't exist
     * @throws ResponseStatusException 403 when {@code currentUserId} isn't the owner
     */
    public void deleteBlog(String blogId, String currentUserId) {
        if (blogId == null || blogId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BlogId required");
        }
        Blog existing = blogRepository.findById(blogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found"));

        // Spring Data converts a Mongo ObjectId-typed userId into its hex String
        // when reading the doc, so a plain String compare works for blogs
        // authored under either the Node or the Java era.
        if (currentUserId == null || !currentUserId.equals(existing.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this blog");
        }

        if (existing.isHidden()) {
            return;
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(blogId)),
                new Update().set("hidden", true),
                Blog.class
        );
    }

    // ---------------- list endpoints (Day 5) ----------------

    /**
     * Returns a user's blogs split into Published / Draft (no profile fields).
     * Used internally by user-service to populate {@code GET /api/v2/Author/:id}.
     * When {@code publishedOnly} is true, the {@code Draft} list is always empty
     * — matches Node's {@code GetAuthor} which hides drafts from non-owners.
     */
    public Map<String, List<BlogListItem>> getBlogsForUser(String userId, boolean publishedOnly) {
        UserClient.UserSummary me = userClient.getSummary(userId).orElse(null);

        Criteria criteria = new Criteria().andOperator(userIdMatches(userId), notHidden());
        if (publishedOnly) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("isPublished").is(true));
        }
        List<Blog> blogs = mongoTemplate.find(Query.query(criteria), Blog.class);

        BlogListItem.AuthorRef ref = me != null
                ? new BlogListItem.AuthorRef(me.id(), me.name(), me.profilePhoto())
                : BlogListItem.AuthorRef.unknown(userId);

        List<BlogListItem> published = new ArrayList<>();
        List<BlogListItem> draft = new ArrayList<>();
        for (Blog b : blogs) {
            BlogListItem item = toListItem(b, ref);
            if (b.isPublished()) {
                published.add(item);
            } else if (!publishedOnly) {
                draft.add(item);
            }
        }

        Map<String, List<BlogListItem>> result = new java.util.LinkedHashMap<>();
        result.put("Published", published);
        result.put("Draft", draft);
        return result;
    }

    /** {@code GET /api/v2/get-user-blogs} — current user's blogs split into Published/Draft. */
    public UserBlogsResponse getUserBlogs(String userId) {
        UserClient.UserSummary me = userClient.getSummary(userId).orElse(null);
        UserClient.AuthorView profile = userClient.getAuthor(userId).orElse(null);

        List<Blog> blogs = mongoTemplate.find(
                Query.query(new Criteria().andOperator(userIdMatches(userId), notHidden())),
                Blog.class
        );

        List<BlogListItem> published = new ArrayList<>();
        List<BlogListItem> draft = new ArrayList<>();
        BlogListItem.AuthorRef ref = me != null
                ? new BlogListItem.AuthorRef(me.id(), me.name(), me.profilePhoto())
                : BlogListItem.AuthorRef.unknown(userId);
        for (Blog b : blogs) {
            BlogListItem item = toListItem(b, ref);
            if (b.isPublished()) {
                published.add(item);
            } else {
                draft.add(item);
            }
        }

        String name = me != null ? me.name() : (profile != null ? profile.name() : "");
        String profilePhoto = me != null ? me.profilePhoto() : (profile != null ? profile.profilePhoto() : "");
        String coverPhoto = profile != null ? profile.coverPhoto() : "";
        String about = profile != null ? profile.about() : "";
        return new UserBlogsResponse(name, profilePhoto, coverPhoto, about, published, draft);
    }

    /** {@code GET /api/v2/get-blogs} — all published, no sort (Node parity). */
    public List<BlogListItem> getAllBlogsNoSort() {
        List<Blog> blogs = mongoTemplate.find(
                Query.query(new Criteria().andOperator(
                        Criteria.where("isPublished").is(true),
                        notHidden()
                )),
                Blog.class
        );
        return populateAuthors(blogs);
    }

    /** {@code GET /api/v2/get-all-blogs?search=} — published, sorted DESC by PublishedDate. */
    public List<BlogListItem> getAllPublished(String search) {
        Criteria base = new Criteria().andOperator(
                Criteria.where("isPublished").is(true),
                notHidden()
        );
        Criteria criteria;
        if (search != null && !search.trim().isEmpty()) {
            String esc = Pattern.quote(search.trim());
            criteria = new Criteria().andOperator(base, new Criteria().orOperator(
                    Criteria.where("Title").regex(esc, "i"),
                    Criteria.where("SubText").regex(esc, "i"),
                    Criteria.where("Body").regex(esc, "i"),
                    Criteria.where("Category").regex(esc, "i")
            ));
        } else {
            criteria = base;
        }
        Query q = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "PublishedDate"));
        return populateAuthors(mongoTemplate.find(q, Blog.class));
    }

    /** {@code GET /api/v2/get-categories-blogs/:category}; {@code DefaultOption} = all. */
    public List<BlogListItem> getByCategory(String category) {
        Criteria criteria;
        if (category == null || "DefaultOption".equals(category)) {
            criteria = new Criteria().andOperator(
                    Criteria.where("isPublished").is(true),
                    notHidden()
            );
        } else {
            criteria = new Criteria().andOperator(
                    Criteria.where("Category").is(category),
                    Criteria.where("isPublished").is(true),
                    notHidden()
            );
        }
        Query q = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "PublishedDate"));
        return populateAuthors(mongoTemplate.find(q, Blog.class));
    }

    /** {@code POST /api/v2/publish-blog} body {@code {Blogid}}. */
    public void publishBlog(String blogId) {
        if (blogId == null || blogId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Blogid required");
        }
        Update update = new Update()
                .set("isPublished", true)
                .set("PublishedDate", Instant.now());
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(blogId)),
                update,
                Blog.class
        );
        if (result.getMatchedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found");
        }

        Blog blog = blogRepository.findById(blogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog not found"));

        blogEventPublisher.publish(new BlogPublishedEvent(
                UUID.randomUUID().toString(),
                blog.getId(),
                blog.getUserId(),
                blog.getTitle(),
                blog.getCategory(),
                blog.getPublishedDate() != null ? blog.getPublishedDate() : Instant.now(),
                blog.getCategory() == null || blog.getCategory().isBlank() ? List.of() : List.of(blog.getCategory())
        ));
    }

    /** Drops a Cloudinary public id stored on a blog. Returns it (or null) so the caller can also nuke remote asset. */
    public String getThumbnailPublicId(String blogId) {
        return blogRepository.findById(blogId)
                .map(Blog::getThumbnailPublicId)
                .orElse(null);
    }

    // ---------------- helpers ----------------

    private List<BlogListItem> populateAuthors(List<Blog> blogs) {
        if (blogs.isEmpty()) return List.of();
        Set<String> authorIds = new HashSet<>();
        for (Blog b : blogs) {
            if (b.getUserId() != null) authorIds.add(b.getUserId());
        }
        Map<String, UserClient.UserSummary> summaries = userClient.summariesByIds(authorIds);
        List<BlogListItem> out = new ArrayList<>(blogs.size());
        for (Blog b : blogs) {
            UserClient.UserSummary s = summaries.get(b.getUserId());
            BlogListItem.AuthorRef ref = s != null
                    ? new BlogListItem.AuthorRef(s.id(), s.name(), s.profilePhoto())
                    : BlogListItem.AuthorRef.unknown(b.getUserId());
            out.add(toListItem(b, ref));
        }
        return out;
    }

    private BlogListItem toListItem(Blog b, BlogListItem.AuthorRef author) {
        return new BlogListItem(
                b.getId(),
                author,
                b.getThumbnail(),
                b.getThumbnailPublicId(),
                b.getTitle(),
                b.getSubText(),
                b.getBody(),
                b.getCategory(),
                b.isPublished(),
                b.getLikes() != null ? b.getLikes() : List.of(),
                b.getComments() != null ? b.getComments() : List.of(),
                b.getPublishedDate(),
                b.getCreatedAt()
        );
    }

    private List<Object> populateComments(List<Blog.Comment> comments) {
        if (comments == null || comments.isEmpty()) return List.of();
        Set<String> ids = new HashSet<>();
        for (Blog.Comment c : comments) {
            if (c.getUserId() != null) ids.add(c.getUserId());
        }
        Map<String, UserClient.UserSummary> summaries = userClient.summariesByIds(ids);
        List<Object> out = new ArrayList<>(comments.size());
        for (Blog.Comment c : comments) {
            UserClient.UserSummary s = summaries.get(c.getUserId());
            Map<String, Object> user = new java.util.LinkedHashMap<>();
            user.put("_id", c.getUserId());
            user.put("name", s != null ? s.name() : "");
            user.put("email", s != null ? s.email() : "");
            user.put("profilePhoto", s != null ? s.profilePhoto() : "");
            Map<String, Object> comment = new java.util.LinkedHashMap<>();
            comment.put("_id", c.getId());
            comment.put("userId", user);
            comment.put("text", c.getText());
            comment.put("createdAt", c.getCreatedAt());
            out.add(comment);
        }
        return out;
    }

    private static int compareByCreatedAt(Blog.Comment a, Blog.Comment b) {
        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
        if (a.getCreatedAt() == null) return -1;
        if (b.getCreatedAt() == null) return 1;
        return a.getCreatedAt().compareTo(b.getCreatedAt());
    }

    /**
     * Builds a criterion that matches {@code blogs.userId} regardless of how
     * it's stored. Atlas has a mix: Node/Mongoose stored it as {@code ObjectId},
     * the Java migration stores it as a String. {@code String == ObjectId} is
     * always false in BSON, so a naive {@code is(userId)} would silently miss
     * the older docs.
     */
    /**
     * Matches blogs that are NOT soft-deleted. {@code hidden} is absent on
     * legacy/Node-era docs, so {@code ne(true)} (rather than {@code is(false)})
     * is what we want — it lets through both {@code hidden:false} and the
     * field-missing case.
     */
    private static Criteria notHidden() {
        return Criteria.where("hidden").ne(true);
    }

    private static Criteria userIdMatches(String userId) {
        try {
            ObjectId oid = new ObjectId(userId);
            return new Criteria().orOperator(
                    Criteria.where("userId").is(userId),
                    Criteria.where("userId").is(oid)
            );
        } catch (IllegalArgumentException notAHexId) {
            return Criteria.where("userId").is(userId);
        }
    }

    private String normalizeContent(String content) {
        if (content == null) return "";
        return content.replace("<br>", "<br/>");
    }

    private String formatCreatedAt(ZonedDateTime dt) {
        return MONTH_NAMES[dt.getMonthValue() - 1] + " " + dt.getDayOfMonth() + "," + dt.getYear();
    }
}

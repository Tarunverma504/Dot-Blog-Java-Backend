package com.dotblog.user.service;

import com.dotblog.user.domain.UserProfile;
import com.dotblog.user.web.dto.ProfilePayload;
import com.dotblog.user.web.dto.UploadPhotoRequest;
import com.dotblog.user.web.dto.UploadPhotoResponse;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Profile operations on the shared {@code users} document. All writes use
 * {@code $set}/{@code $push} so we never overwrite auth-service-owned fields
 * (password, otp, verified, ...).
 *
 * <p>Day 6: previous profile/cover photo assets are now destroyed in
 * Cloudinary via media-service. The delete is best-effort so a Cloudinary
 * outage never blocks the user's update.
 */
@Service
public class ProfileService {

    private final MongoTemplate mongoTemplate;
    private final MediaClient mediaClient;

    public ProfileService(MongoTemplate mongoTemplate, MediaClient mediaClient) {
        this.mongoTemplate = mongoTemplate;
        this.mediaClient = mediaClient;
    }

    public ProfilePayload updateAbout(String userId, String aboutRaw) {
        String about = aboutRaw == null ? "" : aboutRaw.trim().replace("<br>", "<br/>");
        Query q = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update().set("about", about);
        UserProfile updated = mongoTemplate.findAndModify(
                q,
                update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                UserProfile.class
        );
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        }
        return ProfilePayload.of(
                updated.getName(),
                updated.getProfilePhoto(),
                updated.getCoverPhoto(),
                updated.getAbout()
        );
    }

    public UploadPhotoResponse updateProfilePhoto(String userId, UploadPhotoRequest body) {
        return updatePhoto(userId, body, "profilePhoto", "profilePhoto_Public_ID", true);
    }

    public UploadPhotoResponse updateCoverPhoto(String userId, UploadPhotoRequest body) {
        return updatePhoto(userId, body, "coverPhoto", "coverPhoto_Public_ID", false);
    }

    private UploadPhotoResponse updatePhoto(String userId,
                                            UploadPhotoRequest body,
                                            String urlField,
                                            String publicIdField,
                                            boolean profilePhoto) {
        String url = body != null && body.url() != null ? body.url() : "";
        String publicId = body != null && body.publicId() != null ? body.publicId() : "";

        // Capture the previous public id so we can destroy it after a successful write.
        UserProfile existing = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(userId)),
                UserProfile.class
        );
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        String prevPublicId = profilePhoto ? existing.getProfilePhotoPublicId() : existing.getCoverPhotoPublicId();

        Query q = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .set(urlField, url)
                .set(publicIdField, publicId);
        UserProfile updated = mongoTemplate.findAndModify(
                q,
                update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                UserProfile.class
        );
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        }

        if (prevPublicId != null && !prevPublicId.isBlank() && !prevPublicId.equals(publicId)) {
            mediaClient.delete(prevPublicId);
        }

        return UploadPhotoResponse.of(url, publicId);
    }

    /** Used by blog-service to append a created blog id to {@code user.posts}. */
    public void appendPost(String userId, String blogId) {
        if (userId == null || userId.isBlank() || blogId == null || blogId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and blogId required");
        }
        Query q = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update().push("posts", blogId);
        var result = mongoTemplate.updateFirst(q, update, UserProfile.class);
        if (result.getMatchedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}

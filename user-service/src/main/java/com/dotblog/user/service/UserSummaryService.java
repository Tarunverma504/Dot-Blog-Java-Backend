package com.dotblog.user.service;

import com.dotblog.user.domain.UserProfile;
import com.dotblog.user.web.dto.UserSummary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserSummaryService {

    private final MongoTemplate mongoTemplate;

    public UserSummaryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Batch lookup. Returns a map keyed by userId; missing ids are omitted. */
    public Map<String, UserSummary> summariesByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Query q = Query.query(Criteria.where("_id").in(ids));
        q.fields().include("_id").include("name").include("email").include("profilePhoto");
        List<UserProfile> profiles = mongoTemplate.find(q, UserProfile.class);
        Map<String, UserSummary> out = new LinkedHashMap<>();
        for (UserProfile p : profiles) {
            out.put(p.getId(), new UserSummary(
                    p.getId(),
                    p.getName() != null ? p.getName() : "",
                    p.getEmail() != null ? p.getEmail() : "",
                    p.getProfilePhoto() != null ? p.getProfilePhoto() : ""
            ));
        }
        return out;
    }
}

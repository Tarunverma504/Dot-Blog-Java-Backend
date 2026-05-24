package com.dotblog.user.service;

import com.dotblog.user.domain.UserProfile;
import com.dotblog.user.repository.UserProfileRepository;
import com.dotblog.user.web.dto.AuthorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class AuthorService {

    private final UserProfileRepository userProfileRepository;
    private final BlogClient blogClient;

    public AuthorService(UserProfileRepository userProfileRepository, BlogClient blogClient) {
        this.userProfileRepository = userProfileRepository;
        this.blogClient = blogClient;
    }

    public AuthorResponse getAuthorById(String id) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"));

        // Node behaviour: viewers see Published only — drafts are private.
        Map<String, List<Map<String, Object>>> blogs = blogClient.getBlogsForUser(id, true);
        List<?> published = blogs.getOrDefault("Published", List.of());

        return AuthorResponse.of(
                profile.getName(),
                profile.getProfilePhoto(),
                profile.getCoverPhoto(),
                profile.getAbout(),
                published
        );
    }
}

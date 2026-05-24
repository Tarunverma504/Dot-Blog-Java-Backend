package com.dotblog.user.repository;

import com.dotblog.user.domain.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
}

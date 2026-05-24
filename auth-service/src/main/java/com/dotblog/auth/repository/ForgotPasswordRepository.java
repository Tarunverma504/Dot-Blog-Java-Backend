package com.dotblog.auth.repository;

import com.dotblog.auth.domain.ForgotPassword;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ForgotPasswordRepository extends MongoRepository<ForgotPassword, String> {
}

package com.dotblog.auth.service;

import com.dotblog.auth.config.AppProperties;
import com.dotblog.auth.domain.ForgotPassword;
import com.dotblog.auth.domain.User;
import com.dotblog.auth.repository.ForgotPasswordRepository;
import com.dotblog.auth.repository.UserRepository;
import com.dotblog.shared.contract.auth.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Pattern;
import com.dotblog.auth.messaging.OtpEventPublisher;
import com.dotblog.events.DeliveryChannel;
import com.dotblog.events.SendOtpEvent;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final String OTP_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int OTP_LENGTH = 6;

    private final UserRepository userRepository;
    private final ForgotPasswordRepository forgotPasswordRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final MongoTemplate mongoTemplate;
    private final SecureRandom random = new SecureRandom();

    private final OtpEventPublisher otpEventPublisher;

    public AuthService(UserRepository userRepository,
                       ForgotPasswordRepository forgotPasswordRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       EncryptionService encryptionService,
                       EmailService emailService,
                       AppProperties appProperties,
                       MongoTemplate mongoTemplate,
                       OtpEventPublisher otpEventPublisher) {
        this.userRepository = userRepository;
        this.forgotPasswordRepository = forgotPasswordRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.mongoTemplate = mongoTemplate;
        this.otpEventPublisher = otpEventPublisher;
    }

    /** Generate 6-char OTP (uppercase + digits, like Node otp-generator). */
    public String generateOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(OTP_CHARS.charAt(random.nextInt(OTP_CHARS.length())));
        }
        return sb.toString();
    }

    public AuthResponse register(RegisterRequest req) {
        String username = req.username() != null ? req.username().trim() : "";
        String email = req.email() != null ? req.email().trim() : "";
        String password = req.password() != null ? req.password().trim() : "";

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please fill all the details");
        }

        if (username.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your name cannot exceeds 30 characters");
        }

        Optional<User> existing = userRepository.findByEmail(email);
        User user;

        if (existing.isPresent()) {
            User u = existing.get();
            if (u.isVerified()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email is Already Registered");
            }
            u.setName(username);
            u.setPassword(passwordEncoder.encode(password));
            u.setOtp(generateOtp());
            user = userRepository.save(u);
        } else {
            user = new User();
            user.setName(username);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setOtp(generateOtp());
            user.setVerified(false);
            user = userRepository.save(user);
        }

        String verificationToken = jwtService.createToken(user.getId());

        otpEventPublisher.publish(buildOtpEvent(user.getId(), user.getEmail(), user.getOtp(), "VERIFY_EMAIL"));

        return AuthResponse.verification(verificationToken);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email() != null ? req.email().trim() : "";
        String password = req.password();

        if (email.isEmpty() || password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please enter email & password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Email or Password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Email or Password");
        }

        String token = jwtService.createToken(user.getId());
        return AuthResponse.of(
                user.getName(),
                user.getProfilePhoto() != null ? user.getProfilePhoto() : "",
                user.getCoverPhoto() != null ? user.getCoverPhoto() : "",
                token,
                user.getId()
        );
    }

    public AuthResponse isAuthenticated(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank() || "null".equals(bearerToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }

        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }

        String userId;
        try {
            userId = jwtService.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User Loggged"));

        return AuthResponse.of(
                user.getName(),
                user.getProfilePhoto() != null ? user.getProfilePhoto() : "",
                user.getCoverPhoto() != null ? user.getCoverPhoto() : "",
                token,
                user.getId()
        );
    }

    public AuthResponse verify(VerifyRequest req) {
        String token = req.verificationToken();
        String otp = req.otp() != null ? req.otp().trim() : "";
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.valueOf(402), "Token not received");
        }
        if (otp.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please fill the Otp");
        }
        String userId;
        try {
            userId = jwtService.getUserIdFromToken(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Otp not Verified");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User not found"));
        if (user.isVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User already exist");
        }
        if (!otp.equals(user.getOtp())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Otp not Verified");
        }
        user.setVerified(true);
        userRepository.save(user);
        String authToken = jwtService.createToken(user.getId());
        return AuthResponse.of(
                user.getName(),
                user.getProfilePhoto() != null ? user.getProfilePhoto() : "",
                user.getCoverPhoto() != null ? user.getCoverPhoto() : "",
                authToken,
                user.getId()
        );
    }

    public void resendOtp(ResendOtpRequest req) {
        String userIdRaw = req.userId();
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "UserId not received");
        }
        String userId = encryptionService.decryptOrPlain(userIdRaw);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Internal server error"));
        String newOtp = generateOtp();
        user.setOtp(newOtp);
        userRepository.save(user);
        // String msg = "Hi,\n Your OTP is: " + newOtp;
        // emailService.sendOtp(user.getEmail(), msg);

        otpEventPublisher.publish(
                buildOtpEvent(user.getId(), user.getEmail(), newOtp, "RESEND"
                )
        );
    }

    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.email() != null ? req.email().trim() : "";
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please fill all the details");
        }
        User user = findVerifiedUserForForgotPassword(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid email");
        }
        ForgotPassword fp = new ForgotPassword();
        fp.setUserId(user.getId());
        fp.setEmail(email);
        fp = forgotPasswordRepository.save(fp);
        String frontendUrl = appProperties.getFrontendUrl();
        String link = frontendUrl + "/reset-password/" + fp.getId();
        // String html = "<p>Hi, Please click the below link to change the password</p><a href='" + link + "'>Reset Password!</a>";
        // boolean sent = emailService.sendResetLink(email, html, "Change account Password request");
        // if (!sent) {
        //     throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Internalserver error");
        // }

        otpEventPublisher.publish(
            buildOtpEvent(user.getId(), email, fp.getId(), "FORGOT_PASSWORD") 
        );


    }

    public String resetPassword(ResetPasswordRequest req) {
        String password = req.password() != null ? req.password().trim() : "";
        String confirmPassword = req.confirmPassword() != null ? req.confirmPassword().trim() : "";
        String id = req.id();
        if (password.isEmpty() || confirmPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please fill all the fields");
        }
        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password doesn't match");
        }
        ForgotPassword fp = id != null ? forgotPasswordRepository.findById(id).orElse(null) : null;
        if (fp == null || fp.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Internalserver error");
        }
        User user = userRepository.findById(fp.getUserId()).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Internalserver error");
        }
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        fp.setExpired(true);
        forgotPasswordRepository.save(fp);
        return "Password updated successfully";
    }

    /**
     * Loads only {@code _id}, {@code email}, {@code verified} from {@code users} so Mongoose {@code posts}
     * ObjectIds cannot break mapping; email match is case-insensitive; {@code verified} accepts boolean, 1, or "true".
     */
    private User findVerifiedUserForForgotPassword(String email) {
        String anchored = "^" + Pattern.quote(email) + "$";
        Criteria verified = new Criteria().orOperator(
                Criteria.where("verified").is(true),
                Criteria.where("verified").is(1),
                Criteria.where("verified").is("true")
        );
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("email").regex(anchored, "i"),
                verified
        ));
        q.fields().include("_id").include("email").include("verified");
        return mongoTemplate.findOne(q, User.class);
    }

    public boolean validateResetLink(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Internalserver error");
        }
        return forgotPasswordRepository.findById(id)
                .map(ForgotPassword::isExpired)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Internalserver error"));
    }

    private SendOtpEvent buildOtpEvent(String userId, String recipient, String otp, String purpose){

        return new SendOtpEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            userId,
            recipient,
            DeliveryChannel.EMAIL,
            otp,
            purpose
        );
    }


}

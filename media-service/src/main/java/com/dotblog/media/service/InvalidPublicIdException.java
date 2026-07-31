package com.dotblog.media.service;

/**
 * Poison-message signal: publicId is structurally invalid and will never succeed
 * on Cloudinary. Listeners should not retry these — send straight to the DLT.
 */
public class InvalidPublicIdException extends RuntimeException {

    public InvalidPublicIdException(String publicId) {
        super("invalid Cloudinary publicId: " + publicId);
    }
}

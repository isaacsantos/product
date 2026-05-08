package com.example.products.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.products.config.CloudinaryProperties;
import com.example.products.exception.CloudinaryDeleteException;
import com.example.products.exception.CloudinaryUploadException;
import com.example.products.exception.InvalidImageTypeException;
import com.example.products.model.CloudinaryUploadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryServiceImpl(CloudinaryProperties props) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", props.getCloudName(),
                "api_key", props.getApiKey(),
                "api_secret", props.getApiSecret()
        ));
    }

    // Package-private constructor for testing
    CloudinaryServiceImpl(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    private static final Map<String, Object> UPLOAD_OPTIONS = ObjectUtils.asMap("angle", "exif");

    @Override
    public CloudinaryUploadResult upload(MultipartFile file) {
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new InvalidImageTypeException(file.getContentType());
        }
        try {
            Map result = cloudinary.uploader().upload(file.getBytes(), UPLOAD_OPTIONS);
            return new CloudinaryUploadResult(
                    (String) result.get("secure_url"),
                    (String) result.get("public_id")
            );
        } catch (IOException e) {
            throw new CloudinaryUploadException("Cloudinary upload failed", e);
        }
    }

    @Override
    public CloudinaryUploadResult upload(byte[] bytes, String contentType) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidImageTypeException(contentType);
        }
        try {
            Map result = cloudinary.uploader().upload(bytes, UPLOAD_OPTIONS);
            return new CloudinaryUploadResult(
                    (String) result.get("secure_url"),
                    (String) result.get("public_id")
            );
        } catch (IOException e) {
            throw new CloudinaryUploadException("Cloudinary upload failed", e);
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            throw new CloudinaryDeleteException("Cloudinary delete failed for: " + publicId, e);
        }
    }
}

package com.example.products.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.products.exception.CloudinaryDeleteException;
import com.example.products.exception.CloudinaryUploadException;
import com.example.products.exception.InvalidImageTypeException;
import com.example.products.model.CloudinaryUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudinaryServiceImplTest {

    @Mock
    Cloudinary cloudinary;

    @Mock
    Uploader uploader;

    CloudinaryServiceImpl service;

    @BeforeEach
    void setUp() {
        when(cloudinary.uploader()).thenReturn(uploader);
        service = new CloudinaryServiceImpl(cloudinary);
    }

    @Test
    void upload_success_returnsResult() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", "data".getBytes());
        Map<String, Object> sdkResult = Map.of("secure_url", "https://res.cloudinary.com/img.jpg", "public_id", "abc123");
        when(uploader.upload(any(byte[].class), any())).thenReturn(sdkResult);

        CloudinaryUploadResult result = service.upload(file);

        assertThat(result.url()).isEqualTo("https://res.cloudinary.com/img.jpg");
        assertThat(result.publicId()).isEqualTo("abc123");
    }

    @Test
    void upload_invalidContentType_throwsInvalidImageTypeException() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(InvalidImageTypeException.class)
                .hasMessageContaining("application/pdf");
    }

    @Test
    void upload_sdkThrowsIOException_throwsCloudinaryUploadException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", "data".getBytes());
        when(uploader.upload(any(byte[].class), any())).thenThrow(new IOException("network error"));

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(CloudinaryUploadException.class)
                .hasMessageContaining("Cloudinary upload failed");
    }

    @Test
    void delete_success_doesNotThrow() throws IOException {
        when(uploader.destroy(eq("abc123"), any())).thenReturn(Map.of("result", "ok"));

        assertThatCode(() -> service.delete("abc123")).doesNotThrowAnyException();
    }

    @Test
    void delete_sdkThrowsIOException_throwsCloudinaryDeleteException() throws IOException {
        when(uploader.destroy(eq("abc123"), any())).thenThrow(new IOException("network error"));

        assertThatThrownBy(() -> service.delete("abc123"))
                .isInstanceOf(CloudinaryDeleteException.class)
                .hasMessageContaining("abc123");
    }
}

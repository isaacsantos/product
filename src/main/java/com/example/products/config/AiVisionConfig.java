package com.example.products.config;

import com.example.products.service.AiVisionService;
import com.example.products.service.GeminiVisionService;
import com.example.products.service.OpenAiVisionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiVisionConfig {

    @Bean
    public AiVisionService aiVisionService(AiProperties aiProperties,
                                           GeminiProperties geminiProperties,
                                           OpenAiProperties openAiProperties) {
        return switch (aiProperties.getProvider().toLowerCase()) {
            case "gemini" -> new GeminiVisionService(geminiProperties);
            case "openai" -> new OpenAiVisionService(openAiProperties);
            default -> throw new IllegalArgumentException(
                    "Unknown AI provider: " + aiProperties.getProvider() + ". Supported: openai, gemini");
        };
    }
}

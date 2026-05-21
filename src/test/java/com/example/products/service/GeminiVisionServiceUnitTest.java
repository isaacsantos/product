package com.example.products.service;

import com.google.genai.types.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiVisionServiceUnitTest {

    @Nested
    @DisplayName("buildPhase1Prompt")
    class BuildPhase1PromptTests {

        @Test
        @DisplayName("should not mention tags")
        void shouldNotMentionTags() {
            String prompt = GeminiVisionService.buildPhase1Prompt(5);
            assertThat(prompt.toLowerCase()).doesNotContain("tag");
        }

        @Test
        @DisplayName("should not mention descriptions")
        void shouldNotMentionDescriptions() {
            String prompt = GeminiVisionService.buildPhase1Prompt(5);
            assertThat(prompt.toLowerCase()).doesNotContain("description");
        }

        @Test
        @DisplayName("should mention Spanish for product names")
        void shouldMentionSpanish() {
            String prompt = GeminiVisionService.buildPhase1Prompt(5);
            assertThat(prompt.toLowerCase()).contains("spanish");
        }
    }

    @Nested
    @DisplayName("buildPhase2Prompt")
    class BuildPhase2PromptTests {

        @Test
        @DisplayName("should mention Spanish")
        void shouldMentionSpanish() {
            String prompt = GeminiVisionService.buildPhase2Prompt(
                    List.of("Product1"), "[{\"id\": 1, \"name\": \"Electronics\"}]");
            assertThat(prompt.toLowerCase()).contains("spanish");
        }

        @Test
        @DisplayName("should mention brief")
        void shouldMentionBrief() {
            String prompt = GeminiVisionService.buildPhase2Prompt(
                    List.of("Product1"), "[{\"id\": 1, \"name\": \"Electronics\"}]");
            assertThat(prompt.toLowerCase()).contains("brief");
        }

        @Test
        @DisplayName("should mention factual")
        void shouldMentionFactual() {
            String prompt = GeminiVisionService.buildPhase2Prompt(
                    List.of("Product1"), "[{\"id\": 1, \"name\": \"Electronics\"}]");
            assertThat(prompt.toLowerCase()).contains("factual");
        }

        @Test
        @DisplayName("should include tag list content")
        void shouldIncludeTagListContent() {
            String tagList = "[{\"id\": 42, \"name\": \"Ropa\"}, {\"id\": 7, \"name\": \"Electrónica\"}]";
            String prompt = GeminiVisionService.buildPhase2Prompt(List.of("Product1"), tagList);
            assertThat(prompt).contains("42");
            assertThat(prompt).contains("Ropa");
            assertThat(prompt).contains("7");
            assertThat(prompt).contains("Electrónica");
        }
    }

    @Nested
    @DisplayName("Schema structure")
    class SchemaStructureTests {

        @Test
        @DisplayName("Phase 1 schema has required fields name and imageIndices")
        void phase1SchemaHasRequiredFields() {
            Schema schema = GeminiVisionService.buildPhase1Schema();

            // Top level is ARRAY
            assertThat(schema.type()).isPresent();

            // Items is OBJECT with properties
            Schema itemSchema = schema.items().orElseThrow();
            assertThat(itemSchema.properties()).isPresent();
            assertThat(itemSchema.properties().get()).containsKey("name");
            assertThat(itemSchema.properties().get()).containsKey("imageIndices");

            // Required fields
            assertThat(itemSchema.required()).isPresent();
            assertThat(itemSchema.required().get()).contains("name", "imageIndices");
        }

        @Test
        @DisplayName("Phase 2 schema has required fields name, description, and tagIds")
        void phase2SchemaHasRequiredFields() {
            Schema schema = GeminiVisionService.buildPhase2Schema();

            // Top level is ARRAY
            assertThat(schema.type()).isPresent();

            // Items is OBJECT with properties
            Schema itemSchema = schema.items().orElseThrow();
            assertThat(itemSchema.properties()).isPresent();
            assertThat(itemSchema.properties().get()).containsKey("name");
            assertThat(itemSchema.properties().get()).containsKey("description");
            assertThat(itemSchema.properties().get()).containsKey("tagIds");

            // Required fields
            assertThat(itemSchema.required()).isPresent();
            assertThat(itemSchema.required().get()).contains("name", "description", "tagIds");
        }
    }
}

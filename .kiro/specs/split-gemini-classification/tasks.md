# Implementation Plan: Split Gemini Classification

## Overview

Refactor `GeminiVisionService.classifyImages()` from a single Gemini API call into two sequential phases: Phase 1 (vision-based product identification and image grouping) and Phase 2 (text-only description generation and tag assignment). The public interface remains unchanged.

## Tasks

- [x] 1. Create intermediate data models
  - [x] 1.1 Create `Phase1Result` model class
    - Create `src/main/java/com/example/products/model/Phase1Result.java`
    - Fields: `String name`, `List<Integer> imageIndices`
    - Use Lombok annotations: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
    - _Requirements: 1.3, 1.4_

  - [x] 1.2 Create `Phase2Result` model class
    - Create `src/main/java/com/example/products/model/Phase2Result.java`
    - Fields: `String name`, `String description`, `List<Long> tagIds`
    - Use Lombok annotations: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
    - _Requirements: 2.3, 2.4_

- [x] 2. Implement prompt builders and response schemas
  - [x] 2.1 Implement `buildPhase1Prompt(int imageCount)` in `GeminiVisionService`
    - Instruct Gemini to group images by product and return names in Spanish + image indices
    - Must NOT mention tags or descriptions
    - _Requirements: 1.2, 5.1, 5.3_

  - [x] 2.2 Implement `buildPhase2Prompt(List<String> productNames, String tagListJson)` in `GeminiVisionService`
    - Instruct Gemini to generate brief, factual Spanish descriptions and assign tag IDs
    - Include the full tag list with IDs and names
    - Must NOT reference images
    - _Requirements: 5.2, 5.4, 5.5_

  - [x] 2.3 Implement Phase 1 response schema builder
    - Create a method that builds the Gemini `Schema` for Phase 1 (array of objects with `name` STRING and `imageIndices` INTEGER ARRAY)
    - Required fields: `name`, `imageIndices`
    - _Requirements: 1.4_

  - [x] 2.4 Implement Phase 2 response schema builder
    - Create a method that builds the Gemini `Schema` for Phase 2 (array of objects with `name` STRING, `description` STRING, `tagIds` INTEGER ARRAY)
    - Required fields: `name`, `description`, `tagIds`
    - _Requirements: 2.4_

- [x] 3. Implement core phase execution methods
  - [x] 3.1 Implement `executePhase1(List<String> imageUrls)`
    - Build `Content` with image parts (from URIs) + Phase 1 text prompt
    - Call Gemini with Phase 1 schema config
    - Parse JSON response into `List<Phase1Result>` using Gson
    - _Requirements: 1.1, 1.3_

  - [x] 3.2 Implement `validatePhase1Coverage(List<Phase1Result> results, int imageCount)`
    - Validate every index from 0 to imageCount-1 appears exactly once across all results
    - Throw `IllegalStateException` with details if validation fails
    - _Requirements: 1.5_

  - [x] 3.3 Implement `executePhase2(List<String> productNames, List<TagResponse> tags)`
    - Build `Content` with text-only parts (no image data)
    - Call Gemini with Phase 2 schema config
    - Parse JSON response into `List<Phase2Result>` using Gson
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.4 Implement `mergeResults(List<Phase1Result> phase1, List<Phase2Result> phase2)`
    - Match Phase 1 and Phase 2 results by product name
    - Combine into `List<AiClassifiedProduct>` with name, description, imageIndices, tagIds
    - Ignore Phase 2 entries with no Phase 1 match (log warning)
    - Throw `IllegalStateException` if a Phase 1 product has no Phase 2 match
    - _Requirements: 2.5, 3.1, 3.3_

- [x] 4. Refactor `classifyImages` orchestration and error handling
  - [x] 4.1 Refactor `classifyImages` to orchestrate Phase 1 → validate → Phase 2 → merge
    - Remove the old single-call implementation
    - Call `executePhase1`, then `validatePhase1Coverage`, then `executePhase2`, then `mergeResults`
    - Maintain the same method signature and return type
    - _Requirements: 3.2, 4.1_

  - [x] 4.2 Implement error handling between phases
    - If Phase 1 fails, propagate exception immediately (do not attempt Phase 2)
    - If Phase 2 fails, log Phase 1 results at WARN level before propagating exception
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Property-based tests (jqwik)
  - [x] 6.1 Write property test for intermediate model serialization round-trip
    - **Property 1: Intermediate model serialization round-trip**
    - Generate arbitrary `Phase1Result` and `Phase2Result` instances, serialize to JSON with Gson, deserialize back, assert equality
    - Use jqwik `@Property` with `@ForAll` and custom `Arbitrary` providers
    - **Validates: Requirements 1.3, 2.3**

  - [x] 6.2 Write property test for image index coverage validation
    - **Property 2: Image index coverage validation accepts valid partitions**
    - Generate valid partitions of {0..N-1} and verify acceptance; generate invalid partitions (gaps, duplicates, out-of-range) and verify rejection
    - **Validates: Requirements 1.5**

  - [x] 6.3 Write property test for merge correctness
    - **Property 3: Merge produces correct combined output**
    - Generate matching Phase1Result and Phase2Result lists, verify merged output has correct fields from each phase
    - **Validates: Requirements 2.5, 3.1**

  - [x] 6.4 Write property test for Phase 2 prompt tag inclusion
    - **Property 4: Phase 2 prompt includes all available tags**
    - Generate arbitrary `TagResponse` lists, verify the Phase 2 prompt string contains every tag ID and name
    - **Validates: Requirements 5.4**

- [x] 7. Unit tests (Mockito)
  - [x] 7.1 Write unit tests for prompt content
    - Test `buildPhase1Prompt` does not mention tags or descriptions
    - Test `buildPhase1Prompt` mentions Spanish for product names
    - Test `buildPhase2Prompt` mentions Spanish, brief, factual
    - Test `buildPhase2Prompt` includes tag list content
    - _Requirements: 1.2, 5.1, 5.3, 5.5_

  - [x] 7.2 Write unit tests for schema structure
    - Test Phase 1 schema has required fields `name` and `imageIndices`
    - Test Phase 2 schema has required fields `name`, `description`, and `tagIds`
    - _Requirements: 1.4, 2.4_

  - [x] 7.3 Write unit tests for error isolation
    - Test Phase 1 failure prevents Phase 2 execution (mock Gemini client to throw on Phase 1)
    - Test Phase 2 failure logs Phase 1 results at WARN level
    - Test Phase 2 failure propagates exception to caller
    - Test `executePhase2` builds Content with no image parts
    - _Requirements: 2.2, 4.1, 4.2, 4.3_

- [x] 8. Integration test with mocked Gemini client
  - [x] 8.1 Write integration test for end-to-end two-phase flow
    - Mock the Gemini `Client` to return predefined Phase 1 and Phase 2 JSON responses
    - Call `classifyImages` and verify the final `List<AiClassifiedProduct>` is correctly assembled
    - Verify Phase 1 request contains image parts + vision prompt
    - Verify Phase 2 request contains only text parts (no images)
    - _Requirements: 1.1, 2.1, 2.2, 3.1, 3.2_

- [x] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- The refactoring is internal to `GeminiVisionService` — no changes to the interface or orchestrator
- Property tests use jqwik 1.9.3 (already in project dependencies)
- All test classes go under `src/test/java/com/example/products/service/`

# Requirements Document

## Introduction

Split the current single Gemini AI vision call into two separate sequential calls to reduce per-call complexity and avoid AI resource exhaustion when processing multiple product images. Phase 1 (vision call) handles image analysis to extract product names and image grouping. Phase 2 (text-only call) uses the product names to generate descriptions and assign tag IDs without re-sending image data.

## Glossary

- **Vision_Service**: The service component (`GeminiVisionService`) responsible for communicating with the Gemini AI API to classify product images.
- **Phase_1_Call**: The first Gemini API call that includes image data and returns product names and image grouping.
- **Phase_2_Call**: The second Gemini API call that is text-only (no images) and returns product descriptions and tag assignments based on product names from Phase 1.
- **Classified_Product**: The data model (`AiClassifiedProduct`) representing a product identified by AI, containing name, description, image indices, and tag IDs.
- **Image_Grouping**: The assignment of image indices to a specific product, indicating which uploaded images belong to the same product.
- **Orchestrator**: The service (`AiProductCreationServiceImpl`) that coordinates the upload, classification, and product creation flow.

## Requirements

### Requirement 1: Phase 1 — Vision-Based Product Identification

**User Story:** As a store administrator, I want the AI to first identify products and group images, so that the expensive vision call focuses only on visual analysis without overloading it with tag/description logic.

#### Acceptance Criteria

1. WHEN a set of image URLs is provided, THE Vision_Service SHALL send a Phase_1_Call to Gemini containing the image data and a prompt requesting only product names and image grouping.
2. THE Phase_1_Call SHALL NOT include tag information or request product descriptions in its prompt.
3. WHEN Gemini returns a Phase_1_Call response, THE Vision_Service SHALL parse the response into a list of intermediate results containing product name and image indices for each identified product.
4. THE Phase_1_Call response schema SHALL enforce that each result contains a non-empty product name and a non-empty array of image indices.
5. WHEN the Phase_1_Call response is parsed, THE Vision_Service SHALL validate that every image index from 0 to N-1 appears in exactly one product group, where N is the total number of images provided.

### Requirement 2: Phase 2 — Text-Based Description and Tag Assignment

**User Story:** As a store administrator, I want the AI to generate descriptions and assign tags using only product names (no images), so that the second call is cheaper, faster, and independent of image data.

#### Acceptance Criteria

1. WHEN Phase_1_Call results are available, THE Vision_Service SHALL send a Phase_2_Call to Gemini containing only the product names and the list of available tags as text input.
2. THE Phase_2_Call SHALL NOT include any image data in the request.
3. WHEN Gemini returns a Phase_2_Call response, THE Vision_Service SHALL parse the response into descriptions and tag ID assignments for each product name.
4. THE Phase_2_Call response schema SHALL enforce that each result contains a product name, a non-empty description, and a tag IDs array (which may be empty).
5. WHEN Phase_2_Call results are parsed, THE Vision_Service SHALL match each result back to its corresponding Phase_1_Call result by product name.

### Requirement 3: Result Merging and Output Compatibility

**User Story:** As a developer, I want the two-phase results merged into the existing `AiClassifiedProduct` model, so that downstream product creation logic remains unchanged.

#### Acceptance Criteria

1. WHEN both Phase_1_Call and Phase_2_Call complete successfully, THE Vision_Service SHALL merge results into a list of Classified_Product objects containing name, description, image indices, and tag IDs.
2. THE Vision_Service SHALL return the merged list through the existing `classifyImages` method signature, maintaining backward compatibility with the Orchestrator.
3. THE Vision_Service SHALL preserve the same `AiClassifiedProduct` data model without requiring changes to downstream consumers.

### Requirement 4: Error Isolation Between Phases

**User Story:** As a developer, I want failures in Phase 2 to not waste the successful Phase 1 results, so that error handling is granular and resource-efficient.

#### Acceptance Criteria

1. IF Phase_1_Call fails, THEN THE Vision_Service SHALL propagate the error without attempting Phase_2_Call.
2. IF Phase_2_Call fails, THEN THE Vision_Service SHALL log the failure with the Phase_1_Call results that were successfully obtained.
3. IF Phase_2_Call fails, THEN THE Vision_Service SHALL propagate the error to the caller.

### Requirement 5: Prompt Separation

**User Story:** As a developer, I want each phase to have its own focused prompt, so that the AI receives clear, scoped instructions per call.

#### Acceptance Criteria

1. THE Vision_Service SHALL use a dedicated prompt for Phase_1_Call that instructs Gemini to analyze images and return only product names and image grouping.
2. THE Vision_Service SHALL use a dedicated prompt for Phase_2_Call that instructs Gemini to generate Spanish descriptions and assign tag IDs based solely on product names.
3. THE Phase_1_Call prompt SHALL instruct Gemini to provide product names in Spanish.
4. THE Phase_2_Call prompt SHALL include the full list of available tags with their IDs and names for tag assignment.
5. THE Phase_2_Call prompt SHALL instruct Gemini to keep descriptions brief, factual, and in Spanish.

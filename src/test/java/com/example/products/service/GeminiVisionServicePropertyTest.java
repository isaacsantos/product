package com.example.products.service;

import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.Phase1Result;
import com.example.products.model.Phase2Result;
import com.example.products.model.TagResponse;
import com.google.gson.Gson;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for the split-gemini-classification feature.
 *
 * Validates: Requirements 1.3, 2.3
 */
@Label("Feature: split-gemini-classification")
class GeminiVisionServicePropertyTest {

    private final Gson gson = new Gson();

    // -------------------------------------------------------------------------
    // Property 1: Intermediate model serialization round-trip
    // Validates: Requirements 1.3, 2.3
    // -------------------------------------------------------------------------

    @Property
    @Label("Property 1: Intermediate model serialization round-trip")
    @Tag("split-gemini-classification")
    @Tag("serialization-round-trip")
    void phase1ResultSerializationRoundTrip(@ForAll("phase1Results") Phase1Result original) {
        String json = gson.toJson(original);
        Phase1Result deserialized = gson.fromJson(json, Phase1Result.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property
    @Label("Property 1: Intermediate model serialization round-trip")
    @Tag("split-gemini-classification")
    @Tag("serialization-round-trip")
    void phase2ResultSerializationRoundTrip(@ForAll("phase2Results") Phase2Result original) {
        String json = gson.toJson(original);
        Phase2Result deserialized = gson.fromJson(json, Phase2Result.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Provide
    Arbitrary<Phase1Result> phase1Results() {
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<List<Integer>> indices = Arbitraries.integers().between(0, 19)
                .list().ofMinSize(1).ofMaxSize(10);
        return Combinators.combine(names, indices).as((name, idx) ->
                Phase1Result.builder().name(name).imageIndices(idx).build());
    }

    @Provide
    Arbitrary<Phase2Result> phase2Results() {
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<String> descriptions = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100);
        Arbitrary<List<Long>> tagIds = Arbitraries.longs().between(1, 100)
                .list().ofMinSize(0).ofMaxSize(5);
        return Combinators.combine(names, descriptions, tagIds).as((name, desc, tags) ->
                Phase2Result.builder().name(name).description(desc).tagIds(tags).build());
    }

    // -------------------------------------------------------------------------
    // Property 2: Image index coverage validation accepts valid partitions
    // Validates: Requirements 1.5
    // -------------------------------------------------------------------------

    /**
     * Validates: Requirements 1.5
     *
     * For any valid partition of {0..N-1} into non-empty disjoint subsets,
     * validatePhase1Coverage SHALL accept it without throwing.
     */
    @Property
    @Label("Property 2: Image index coverage validation accepts valid partitions")
    @Tag("split-gemini-classification")
    @Tag("index-coverage")
    void validPartitionsAreAccepted(@ForAll("validPartitions") ValidPartitionData partition) {
        // Should not throw for a valid partition
        GeminiVisionService.validatePhase1Coverage(partition.results, partition.imageCount);
    }

    /**
     * Validates: Requirements 1.5
     *
     * For any collection of index lists that does NOT form a complete partition
     * of {0..N-1} (missing indices, duplicates, or out-of-range values),
     * validatePhase1Coverage SHALL reject it with IllegalStateException.
     */
    @Property
    @Label("Property 2: Image index coverage validation rejects invalid partitions")
    @Tag("split-gemini-classification")
    @Tag("index-coverage")
    void invalidPartitionsAreRejected(@ForAll("invalidPartitions") InvalidPartitionData partition) {
        assertThatThrownBy(() ->
                GeminiVisionService.validatePhase1Coverage(partition.results, partition.imageCount)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Provide
    Arbitrary<ValidPartitionData> validPartitions() {
        return Arbitraries.integers().between(1, 20).flatMap(imageCount -> {
            // Generate a random partition of {0..imageCount-1} into non-empty groups
            return Arbitraries.integers().between(1, imageCount).flatMap(groupCount -> {
                return Arbitraries.just(imageCount).map(n -> {
                    // Create a shuffled list of all indices
                    List<Integer> allIndices = IntStream.range(0, n)
                            .boxed()
                            .collect(Collectors.toCollection(ArrayList::new));
                    Collections.shuffle(allIndices);

                    // Split into groupCount non-empty groups
                    List<Phase1Result> results = new ArrayList<>();
                    int actualGroups = Math.min(groupCount, n);
                    int baseSize = n / actualGroups;
                    int remainder = n % actualGroups;

                    int offset = 0;
                    for (int g = 0; g < actualGroups; g++) {
                        int size = baseSize + (g < remainder ? 1 : 0);
                        List<Integer> groupIndices = new ArrayList<>(allIndices.subList(offset, offset + size));
                        results.add(Phase1Result.builder()
                                .name("Product" + g)
                                .imageIndices(groupIndices)
                                .build());
                        offset += size;
                    }

                    return new ValidPartitionData(results, n);
                });
            });
        });
    }

    @Provide
    Arbitrary<InvalidPartitionData> invalidPartitions() {
        // Three types of invalid partitions: missing indices, duplicates, out-of-range
        Arbitrary<InvalidPartitionData> withMissing = Arbitraries.integers().between(2, 20).map(imageCount -> {
            // Create a partition that's missing at least one index
            List<Integer> allIndices = IntStream.range(0, imageCount)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            // Remove one random index to create a gap
            allIndices.remove(allIndices.size() - 1);
            List<Phase1Result> results = List.of(Phase1Result.builder()
                    .name("Product0")
                    .imageIndices(allIndices)
                    .build());
            return new InvalidPartitionData(results, imageCount);
        });

        Arbitrary<InvalidPartitionData> withDuplicates = Arbitraries.integers().between(2, 20).map(imageCount -> {
            // Create a partition with a duplicate index
            List<Integer> indices1 = new ArrayList<>();
            indices1.add(0);
            indices1.add(1);
            List<Integer> indices2 = IntStream.range(1, imageCount)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            List<Phase1Result> results = List.of(
                    Phase1Result.builder().name("Product0").imageIndices(indices1).build(),
                    Phase1Result.builder().name("Product1").imageIndices(indices2).build()
            );
            return new InvalidPartitionData(results, imageCount);
        });

        Arbitrary<InvalidPartitionData> withOutOfRange = Arbitraries.integers().between(1, 20).map(imageCount -> {
            // Create a partition with an out-of-range index
            List<Integer> allIndices = IntStream.range(0, imageCount)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));
            // Replace last index with an out-of-range value
            allIndices.set(allIndices.size() - 1, imageCount + 5);
            List<Phase1Result> results = List.of(Phase1Result.builder()
                    .name("Product0")
                    .imageIndices(allIndices)
                    .build());
            return new InvalidPartitionData(results, imageCount);
        });

        return Arbitraries.oneOf(withMissing, withDuplicates, withOutOfRange);
    }

    // Helper data classes for Property 2
    static class ValidPartitionData {
        final List<Phase1Result> results;
        final int imageCount;

        ValidPartitionData(List<Phase1Result> results, int imageCount) {
            this.results = results;
            this.imageCount = imageCount;
        }

        @Override
        public String toString() {
            return "ValidPartition{imageCount=" + imageCount + ", results=" + results + "}";
        }
    }

    static class InvalidPartitionData {
        final List<Phase1Result> results;
        final int imageCount;

        InvalidPartitionData(List<Phase1Result> results, int imageCount) {
            this.results = results;
            this.imageCount = imageCount;
        }

        @Override
        public String toString() {
            return "InvalidPartition{imageCount=" + imageCount + ", results=" + results + "}";
        }
    }

    // -------------------------------------------------------------------------
    // Property 3: Merge produces correct combined output
    // Validates: Requirements 2.5, 3.1
    // -------------------------------------------------------------------------

    @Property
    @Label("Property 3: Merge produces correct combined output")
    @Tag("split-gemini-classification")
    @Tag("merge-correctness")
    void mergeProducesCorrectCombinedOutput(@ForAll("matchingPhaseResults") MatchingPhaseData data) {
        List<AiClassifiedProduct> merged = GeminiVisionService.mergeResults(data.phase1, data.phase2);

        assertThat(merged).hasSize(data.phase1.size());

        for (int i = 0; i < merged.size(); i++) {
            AiClassifiedProduct product = merged.get(i);
            Phase1Result p1 = data.phase1.get(i);
            // Find matching Phase2Result by name
            Phase2Result p2 = data.phase2.stream()
                    .filter(r -> r.getName().equals(p1.getName()))
                    .findFirst().orElseThrow();

            assertThat(product.getName()).isEqualTo(p1.getName());
            assertThat(product.getImageIndices()).isEqualTo(p1.getImageIndices());
            assertThat(product.getDescription()).isEqualTo(p2.getDescription());
            assertThat(product.getTagIds()).isEqualTo(p2.getTagIds());
        }
    }

    @Provide
    Arbitrary<MatchingPhaseData> matchingPhaseResults() {
        return Arbitraries.integers().between(1, 10).flatMap(count -> {
            // Generate unique product names
            Arbitrary<List<String>> namesArb = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                    .list().ofSize(count).uniqueElements();

            return namesArb.flatMap(names -> {
                // Generate Phase1Results with those names
                Arbitrary<List<Phase1Result>> phase1Arb = Arbitraries.just(names).map(n -> {
                    List<Phase1Result> results = new ArrayList<>();
                    for (String name : n) {
                        List<Integer> indices = List.of(results.size()); // simple unique index
                        results.add(Phase1Result.builder().name(name).imageIndices(indices).build());
                    }
                    return results;
                });

                // Generate Phase2Results with matching names
                Arbitrary<List<Phase2Result>> phase2Arb = Arbitraries.just(names).flatMap(n -> {
                    List<Arbitrary<Phase2Result>> arbitraries = new ArrayList<>();
                    for (String name : n) {
                        Arbitrary<Phase2Result> p2 = Combinators.combine(
                                Arbitraries.just(name),
                                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50),
                                Arbitraries.longs().between(1, 50).list().ofMinSize(0).ofMaxSize(3)
                        ).as((nm, desc, tags) -> Phase2Result.builder().name(nm).description(desc).tagIds(tags).build());
                        arbitraries.add(p2);
                    }
                    // Combine all into a list
                    @SuppressWarnings("unchecked")
                    Arbitrary<List<Phase2Result>> combined = (Arbitrary<List<Phase2Result>>) (Arbitrary<?>)
                            Combinators.combine(arbitraries).flatAs(list -> Arbitraries.just(list));
                    return combined;
                });

                return Combinators.combine(phase1Arb, phase2Arb).as(MatchingPhaseData::new);
            });
        });
    }

    // Helper data class for Property 3
    static class MatchingPhaseData {
        final List<Phase1Result> phase1;
        final List<Phase2Result> phase2;

        MatchingPhaseData(List<Phase1Result> phase1, List<Phase2Result> phase2) {
            this.phase1 = phase1;
            this.phase2 = phase2;
        }

        @Override
        public String toString() {
            return "MatchingPhaseData{phase1=" + phase1 + ", phase2=" + phase2 + "}";
        }
    }

    // -------------------------------------------------------------------------
    // Property 4: Phase 2 prompt includes all available tags
    // Validates: Requirements 5.4
    // -------------------------------------------------------------------------

    @Property
    @Label("Property 4: Phase 2 prompt includes all available tags")
    @Tag("split-gemini-classification")
    @Tag("prompt-tag-inclusion")
    void phase2PromptIncludesAllTags(@ForAll("tagResponseLists") List<TagResponse> tags) {
        List<String> productNames = List.of("TestProduct");
        String tagListJson = tags.stream()
                .map(t -> String.format("{\"id\": %d, \"name\": \"%s\"}", t.getId(), t.getName()))
                .collect(Collectors.joining(", ", "[", "]"));

        String prompt = GeminiVisionService.buildPhase2Prompt(productNames, tagListJson);

        for (TagResponse tag : tags) {
            assertThat(prompt).contains(String.valueOf(tag.getId()));
            assertThat(prompt).contains(tag.getName());
        }
    }

    @Provide
    Arbitrary<List<TagResponse>> tagResponseLists() {
        Arbitrary<TagResponse> tagArb = Combinators.combine(
                Arbitraries.longs().between(1, 1000),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
        ).as((id, name) -> TagResponse.builder().id(id).name(name).type("category").build());

        return tagArb.list().ofMinSize(1).ofMaxSize(10);
    }
}

package com.pranicdoc.cph.review.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotHasherTest {

    @Test
    void keyOrderDoesNotMatter_atAnyLevel() {
        String a = "{\"a\":1,\"b\":{\"x\":[1,2],\"y\":\"s\"}}";
        String b = "{\"b\":{\"y\":\"s\",\"x\":[1,2]},\"a\":1}";
        assertThat(SnapshotHasher.hash(a)).isEqualTo(SnapshotHasher.hash(b));
    }

    @Test
    void whitespaceDoesNotMatter() {
        assertThat(SnapshotHasher.hash("{\"a\": 1,\n \"b\" : [ 1, 2 ]}"))
                .isEqualTo(SnapshotHasher.hash("{\"a\":1,\"b\":[1,2]}"));
    }

    @Test
    void aChangedValueChangesTheHash() {
        assertThat(SnapshotHasher.hash("{\"a\":1}")).isNotEqualTo(SnapshotHasher.hash("{\"a\":2}"));
    }

    @Test
    void arrayOrderStillMatters() {
        assertThat(SnapshotHasher.hash("{\"a\":[1,2]}")).isNotEqualTo(SnapshotHasher.hash("{\"a\":[2,1]}"));
    }

    @Test
    void aVolatileFieldIsIgnoredAtAnyDepth() {
        Set<String> volatileFields = Set.of("generatedAt");
        String a = "{\"a\":1,\"generatedAt\":\"t1\",\"n\":{\"generatedAt\":\"t1\",\"k\":2}}";
        String b = "{\"a\":1,\"generatedAt\":\"t2\",\"n\":{\"generatedAt\":\"t2\",\"k\":2}}";
        assertThat(SnapshotHasher.hash(a, volatileFields)).isEqualTo(SnapshotHasher.hash(b, volatileFields));
        assertThat(SnapshotHasher.hash(a)).isNotEqualTo(SnapshotHasher.hash(b));
    }

    @Test
    void isSha256Hex() {
        assertThat(SnapshotHasher.hash("{}")).matches("[0-9a-f]{64}");
    }

    @Test
    void missingOrUnreadableDataHasNoHash() {
        assertThat(SnapshotHasher.hash(null)).isNull();
        assertThat(SnapshotHasher.hash(" ")).isNull();
        assertThat(SnapshotHasher.hash("{not json")).isNull();
    }
}

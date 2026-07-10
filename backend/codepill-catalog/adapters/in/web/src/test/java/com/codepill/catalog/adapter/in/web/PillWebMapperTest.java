package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillSnapshot;
import com.codepill.catalog.adapter.in.web.dto.PagedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PillWebMapperTest {

    private final PillWebMapper mapper = new PillWebMapper(new ObjectMapper());

    @Test
    void shouldFailLoudly_whenStoredContentIsNotJson() {
        var corrupted = Pill.fromSnapshot(new PillSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), "t", "t", null,
                "this is not json", "ARTICLE", "DRAFT", 60, null, null, null, 0L));

        assertThatThrownBy(() -> mapper.toResponse(corrupted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailLoudly_whenContentMapIsNotSerializable() {
        var request = new com.codepill.catalog.adapter.in.web.dto.CreatePillRequest(
                "Title", "slug", null, Map.of("bad", new Object()),
                com.codepill.catalog.domain.PillType.ARTICLE, 60);

        assertThatThrownBy(() -> mapper.toCommand(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pagedResponseShouldHandleZeroSize() {
        assertThat(PagedResponse.of(List.of(), 0, 0, 0).totalPages()).isZero();
    }
}

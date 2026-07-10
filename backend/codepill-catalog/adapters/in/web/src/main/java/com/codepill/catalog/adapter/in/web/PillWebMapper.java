package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.adapter.in.web.dto.CreatePillRequest;
import com.codepill.catalog.adapter.in.web.dto.PillResponse;
import com.codepill.catalog.adapter.in.web.dto.UpdatePillRequest;
import com.codepill.catalog.application.usecase.CreatePillUseCase.CreatePillCommand;
import com.codepill.catalog.application.usecase.UpdatePillUseCase.UpdatePillCommand;
import com.codepill.catalog.domain.Pill;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/** Maps between web DTOs and application commands / domain objects. */
final class PillWebMapper {

    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> MAP_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    PillWebMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    CreatePillCommand toCommand(CreatePillRequest request) {
        return new CreatePillCommand(
                request.title(),
                request.slug(),
                request.summary(),
                toJson(request.content()),
                request.type(),
                request.estimatedDurationSeconds());
    }

    UpdatePillCommand toCommand(UUID pillId, UpdatePillRequest request) {
        return new UpdatePillCommand(
                pillId,
                request.title(),
                request.slug(),
                request.summary(),
                toJson(request.content()),
                request.type(),
                request.estimatedDurationSeconds());
    }

    PillResponse toResponse(Pill pill) {
        var snapshot = pill.snapshot();
        return new PillResponse(
                snapshot.id(),
                snapshot.authorId(),
                snapshot.title(),
                snapshot.slug(),
                snapshot.summary(),
                toMap(snapshot.content()),
                snapshot.pillType(),
                snapshot.status(),
                snapshot.estimatedDurationSeconds(),
                snapshot.publishedAt(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.version());
    }

    private String toJson(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("content map could not be serialized", e);
        }
    }

    private Map<String, Object> toMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored pill content is not a JSON object", e);
        }
    }
}

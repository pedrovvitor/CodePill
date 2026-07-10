package com.codepill.catalog.adapter.in.web.dto;

import com.codepill.catalog.domain.PillType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body of {@code POST /api/v1/pills}. {@code content} is the pill body as a
 * JSON object — binding it as a map guarantees well-formed JSON at the edge
 * (SECURITY.md §4.2 input validation); domain invariants re-validate depth.
 */
public record CreatePillRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*",
                message = "must be lowercase alphanumeric words separated by single hyphens")
        String slug,
        @Size(max = 500) String summary,
        @NotNull Map<String, Object> content,
        @NotNull PillType type,
        @Positive @Max(86_400) int estimatedDurationSeconds) {
}

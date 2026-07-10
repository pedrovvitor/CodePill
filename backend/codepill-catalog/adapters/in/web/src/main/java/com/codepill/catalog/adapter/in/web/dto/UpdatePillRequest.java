package com.codepill.catalog.adapter.in.web.dto;

import com.codepill.catalog.domain.PillType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Body of {@code PUT /api/v1/pills/{id}} — full replacement of editable fields. */
public record UpdatePillRequest(
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

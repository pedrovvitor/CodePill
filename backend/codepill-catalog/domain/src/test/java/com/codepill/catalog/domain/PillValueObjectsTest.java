package com.codepill.catalog.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PillValueObjectsTest {

    @Nested
    class Titles {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void shouldRejectBlank(String value) {
            assertThatThrownBy(() -> new Title(value))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void shouldRejectOverlong() {
            assertThatThrownBy(() -> new Title("x".repeat(Title.MAX_LENGTH + 1)))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void shouldStripWhitespace() {
            assertThat(new Title("  Hello  ").value()).isEqualTo("Hello");
        }

        @Test
        void shouldAcceptMaxLength() {
            assertThat(new Title("x".repeat(Title.MAX_LENGTH)).value()).hasSize(Title.MAX_LENGTH);
        }
    }

    @Nested
    class Slugs {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "UPPER", "two--hyphens", "-leading", "trailing-", "with space", "unicode-café"})
        void shouldRejectInvalid(String value) {
            assertThatThrownBy(() -> new Slug(value))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void shouldRejectOverlong() {
            assertThatThrownBy(() -> new Slug("a".repeat(Slug.MAX_LENGTH + 1)))
                    .isInstanceOf(DomainValidationException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "java-25", "spring-boot-4-in-5-minutes", "abc123"})
        void shouldAcceptValid(String value) {
            assertThat(new Slug(value).value()).isEqualTo(value);
        }
    }

    @Nested
    class Summaries {

        @Test
        void shouldMapBlankOrNullToNull() {
            assertThat(Summary.ofNullable(null)).isNull();
            assertThat(Summary.ofNullable("   ")).isNull();
            assertThat(Summary.ofNullable("")).isNull();
        }

        @Test
        void shouldRejectDirectBlankConstruction() {
            assertThatThrownBy(() -> new Summary(" "))
                    .isInstanceOf(DomainValidationException.class);
            assertThatThrownBy(() -> new Summary(null))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void shouldRejectOverlong() {
            assertThatThrownBy(() -> Summary.ofNullable("x".repeat(Summary.MAX_LENGTH + 1)))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void shouldStripAndKeepValue() {
            assertThat(Summary.ofNullable(" concise ")).isEqualTo(new Summary("concise"));
        }
    }

    @Nested
    class Contents {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void shouldRejectBlank(String value) {
            assertThatThrownBy(() -> new PillContent(value))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void shouldProvideEmptyDocument() {
            assertThat(PillContent.empty().json()).isEqualTo("{}");
        }

        @Test
        void shouldRejectContent_exceedingMaxLength() {
            var oversized = "{\"body\":\"" + "x".repeat(PillContent.MAX_LENGTH) + "\"}";

            assertThatThrownBy(() -> new PillContent(oversized))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("content");
        }

        @Test
        void shouldAcceptContent_atMaxLength() {
            var padding = PillContent.MAX_LENGTH - "{\"body\":\"\"}".length();
            var content = new PillContent("{\"body\":\"" + "x".repeat(padding) + "\"}");

            assertThat(content.json()).hasSize(PillContent.MAX_LENGTH);
        }
    }

    @Nested
    class Durations {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, EstimatedDuration.MAX_SECONDS + 1})
        void shouldRejectOutOfRange(int seconds) {
            assertThatThrownBy(() -> new EstimatedDuration(seconds))
                    .isInstanceOf(DomainValidationException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 300, EstimatedDuration.MAX_SECONDS})
        void shouldAcceptInRange(int seconds) {
            assertThat(new EstimatedDuration(seconds).seconds()).isEqualTo(seconds);
        }
    }

    @Nested
    class Identifiers {

        @Test
        void shouldRejectNullUuid() {
            assertThatThrownBy(() -> PillId.of(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> AuthorId.of(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldGenerateDistinctIds() {
            assertThat(PillId.newId()).isNotEqualTo(PillId.newId());
        }

        @Test
        void shouldWrapValue() {
            var uuid = UUID.randomUUID();
            assertThat(PillId.of(uuid).value()).isEqualTo(uuid);
            assertThat(AuthorId.of(uuid).value()).isEqualTo(uuid);
        }
    }
}

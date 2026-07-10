package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.application.port.out.PillPage;
import com.codepill.catalog.application.security.Caller;
import com.codepill.catalog.application.security.Role;
import com.codepill.catalog.application.usecase.CreatePillUseCase;
import com.codepill.catalog.application.usecase.CreatePillUseCase.CreatePillCommand;
import com.codepill.catalog.application.usecase.DeletePillUseCase;
import com.codepill.catalog.application.usecase.GetPillUseCase;
import com.codepill.catalog.application.usecase.ListPillsUseCase;
import com.codepill.catalog.application.usecase.PublishPillUseCase;
import com.codepill.catalog.application.usecase.UpdatePillUseCase;
import com.codepill.catalog.application.usecase.UpdatePillUseCase.UpdatePillCommand;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillLifecycleException;
import com.codepill.catalog.domain.PillStatus;
import com.codepill.catalog.domain.Slug;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PillControllerTest {

    @Mock CreatePillUseCase createPill;
    @Mock GetPillUseCase getPill;
    @Mock ListPillsUseCase listPills;
    @Mock UpdatePillUseCase updatePill;
    @Mock DeletePillUseCase deletePill;
    @Mock PublishPillUseCase publishPill;

    MockMvc mockMvc;

    private static final String VALID_BODY = """
            {
              "title": "Virtual Threads in 5 Minutes",
              "slug": "virtual-threads-in-5-minutes",
              "summary": "Java 25 virtual threads, condensed.",
              "content": {"blocks": [1]},
              "type": "ARTICLE",
              "estimatedDurationSeconds": 300
            }
            """;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var jackson = new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // flatten ProblemDetail extension properties like Boot does at runtime
                .addMixIn(org.springframework.http.ProblemDetail.class,
                        org.springframework.http.converter.json.ProblemDetailJacksonMixin.class)
                .build());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PillController(createPill, getPill, listPills,
                        updatePill, deletePill, publishPill))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(jackson)
                .build();
    }

    @Test
    void shouldCreatePill_return201WithLocation_andMapCallerFromJwt() throws Exception {
        var pill = WebTestFixtures.pill(WebTestFixtures.USER, PillStatus.DRAFT);
        when(createPill.create(any(), any())).thenReturn(pill);

        mockMvc.perform(post("/api/v1/pills")
                        .principal(WebTestFixtures.tokenWithRoles(List.of("AUTHOR", "LEARNER", "SUPERHERO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/pills/" + pill.id().value()))
                .andExpect(jsonPath("$.slug").value("virtual-threads-in-5-minutes"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.content.blocks[0]").value(1))
                .andExpect(jsonPath("$.createdAt").value("2026-07-01T08:00:00Z"));

        var command = ArgumentCaptor.forClass(CreatePillCommand.class);
        var caller = ArgumentCaptor.forClass(Caller.class);
        verify(createPill).create(command.capture(), caller.capture());
        assertThat(command.getValue().content()).isEqualTo("{\"blocks\":[1]}");
        assertThat(caller.getValue().userId()).isEqualTo(WebTestFixtures.USER);
        // unknown IdP roles are dropped, known ones parsed
        assertThat(caller.getValue().roles()).containsExactlyInAnyOrder(Role.AUTHOR, Role.LEARNER);
    }

    @Test
    void shouldRejectInvalidBody_withFieldErrors_withoutInvokingUseCase() throws Exception {
        var invalid = """
                {"title": "", "slug": "Bad Slug!", "content": {"a":1}, "type": "ARTICLE",
                 "estimatedDurationSeconds": -5}
                """;

        mockMvc.perform(post("/api/v1/pills")
                        .principal(WebTestFixtures.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.slug").exists())
                .andExpect(jsonPath("$.errors.estimatedDurationSeconds").exists());

        verifyNoInteractions(createPill);
    }

    @Test
    void shouldRejectMalformedJson_as400() throws Exception {
        mockMvc.perform(post("/api/v1/pills")
                        .principal(WebTestFixtures.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void shouldGetPill_withContentAsJsonObject() throws Exception {
        var pill = WebTestFixtures.pill(WebTestFixtures.USER, PillStatus.PUBLISHED);
        when(getPill.get(any(), any())).thenReturn(pill);

        mockMvc.perform(get("/api/v1/pills/{id}", pill.id().value())
                        .principal(WebTestFixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pill.id().value().toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value("2026-07-09T12:00:00Z"))
                .andExpect(jsonPath("$.content.blocks[0]").value(1));

        verify(getPill).get(PillId.of(pill.id().value()), CallerMapper.from(WebTestFixtures.token()));
    }

    @Test
    void shouldMapNotFound_to404Problem() throws Exception {
        when(getPill.get(any(), any())).thenThrow(new PillNotFoundException(PillId.newId()));

        mockMvc.perform(get("/api/v1/pills/{id}", UUID.randomUUID())
                        .principal(WebTestFixtures.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Pill not found"));
    }

    @Test
    void shouldRejectNonUuidPathVariable_as400() throws Exception {
        mockMvc.perform(get("/api/v1/pills/not-a-uuid").principal(WebTestFixtures.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(getPill);
    }

    @Test
    void shouldListPills_withPagingMetadata() throws Exception {
        var published = WebTestFixtures.pill(WebTestFixtures.USER, PillStatus.PUBLISHED);
        when(listPills.list(1, 10)).thenReturn(new PillPage(List.of(published), 42, 1, 10));

        mockMvc.perform(get("/api/v1/pills").param("page", "1").param("size", "10")
                        .principal(WebTestFixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("virtual-threads-in-5-minutes"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.totalPages").value(5));
    }

    @Test
    void shouldUpdatePill_passingPathIdIntoCommand() throws Exception {
        var pill = WebTestFixtures.pill(WebTestFixtures.USER, PillStatus.DRAFT);
        when(updatePill.update(any(), any())).thenReturn(pill);

        mockMvc.perform(put("/api/v1/pills/{id}", pill.id().value())
                        .principal(WebTestFixtures.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pill.id().value().toString()));

        var command = ArgumentCaptor.forClass(UpdatePillCommand.class);
        verify(updatePill).update(command.capture(), any());
        assertThat(command.getValue().pillId()).isEqualTo(pill.id().value());
    }

    @Test
    void shouldMapSlugConflict_to409Problem() throws Exception {
        when(updatePill.update(any(), any()))
                .thenThrow(new SlugAlreadyInUseException(new Slug("taken-slug")));

        mockMvc.perform(put("/api/v1/pills/{id}", UUID.randomUUID())
                        .principal(WebTestFixtures.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Slug already in use"));
    }

    @Test
    void shouldMapLifecycleViolation_to409Problem() throws Exception {
        when(updatePill.update(any(), any()))
                .thenThrow(new PillLifecycleException("an archived pill cannot be edited"));

        mockMvc.perform(put("/api/v1/pills/{id}", UUID.randomUUID())
                        .principal(WebTestFixtures.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operation not allowed in current state"));
    }

    @Test
    void shouldMapOptimisticLockConflict_to409Problem() throws Exception {
        when(updatePill.update(any(), any()))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        mockMvc.perform(put("/api/v1/pills/{id}", UUID.randomUUID())
                        .principal(WebTestFixtures.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    void shouldMapOwnershipDenial_to403Problem() throws Exception {
        when(updatePill.update(any(), any()))
                .thenThrow(new AccessDeniedException("caller is not the author of this pill"));

        mockMvc.perform(put("/api/v1/pills/{id}", UUID.randomUUID())
                        .principal(WebTestFixtures.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"))
                // no internal information in the response (SECURITY.md §3.2 rule 2)
                .andExpect(jsonPath("$.detail").value("not allowed to perform this operation"));
    }

    @Test
    void shouldDeletePill_return204() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/pills/{id}", id).principal(WebTestFixtures.token()))
                .andExpect(status().isNoContent());

        verify(deletePill).delete(PillId.of(id), CallerMapper.from(WebTestFixtures.token()));
    }

    @Test
    void shouldPublishPill_return200() throws Exception {
        var pill = WebTestFixtures.pill(WebTestFixtures.USER, PillStatus.PUBLISHED);
        when(publishPill.publish(any(), any())).thenReturn(pill);

        mockMvc.perform(post("/api/v1/pills/{id}/publish", pill.id().value())
                        .principal(WebTestFixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void shouldMapUnexpectedError_to500Problem_withoutLeakingDetails() throws Exception {
        when(getPill.get(any(), any())).thenThrow(new RuntimeException("connection refused to db-secret-host"));

        mockMvc.perform(get("/api/v1/pills/{id}", UUID.randomUUID())
                        .principal(WebTestFixtures.token()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Unexpected error"))
                .andExpect(jsonPath("$.detail").value("an unexpected error occurred"));
    }
}

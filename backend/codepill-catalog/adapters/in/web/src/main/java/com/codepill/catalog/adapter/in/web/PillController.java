package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.adapter.in.web.dto.CreatePillRequest;
import com.codepill.catalog.adapter.in.web.dto.PagedResponse;
import com.codepill.catalog.adapter.in.web.dto.PillResponse;
import com.codepill.catalog.adapter.in.web.dto.UpdatePillRequest;
import com.codepill.catalog.application.usecase.CreatePillUseCase;
import com.codepill.catalog.application.usecase.DeletePillUseCase;
import com.codepill.catalog.application.usecase.GetPillUseCase;
import com.codepill.catalog.application.usecase.ListPillsUseCase;
import com.codepill.catalog.application.usecase.PublishPillUseCase;
import com.codepill.catalog.application.usecase.UpdatePillUseCase;
import com.codepill.catalog.domain.PillId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * CRUD + publish for pills under {@code /api/v1/pills}. Controllers only
 * translate HTTP ⇄ use cases; authorization lives on the use cases
 * (SECURITY.md §3.2) behind the deny-by-default filter chain.
 */
@RestController
@RequestMapping("/api/v1/pills")
class PillController {

    private final CreatePillUseCase createPill;
    private final GetPillUseCase getPill;
    private final ListPillsUseCase listPills;
    private final UpdatePillUseCase updatePill;
    private final DeletePillUseCase deletePill;
    private final PublishPillUseCase publishPill;
    private final PillWebMapper mapper;

    PillController(CreatePillUseCase createPill, GetPillUseCase getPill,
                   ListPillsUseCase listPills, UpdatePillUseCase updatePill,
                   DeletePillUseCase deletePill, PublishPillUseCase publishPill) {
        this.createPill = createPill;
        this.getPill = getPill;
        this.listPills = listPills;
        this.updatePill = updatePill;
        this.deletePill = deletePill;
        this.publishPill = publishPill;
        // private Jackson 2 instance for content-map round-trips only; the MVC
        // message conversion itself uses whatever the runtime provides
        this.mapper = new PillWebMapper(new ObjectMapper());
    }

    @PostMapping
    ResponseEntity<PillResponse> create(@Valid @RequestBody CreatePillRequest request,
                                        JwtAuthenticationToken authentication) {
        var pill = createPill.create(mapper.toCommand(request), CallerMapper.from(authentication));
        return ResponseEntity
                .created(URI.create("/api/v1/pills/" + pill.id().value()))
                .body(mapper.toResponse(pill));
    }

    @GetMapping("/{id}")
    PillResponse get(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return mapper.toResponse(getPill.get(PillId.of(id), CallerMapper.from(authentication)));
    }

    @GetMapping
    PagedResponse<PillResponse> list(
            @RequestParam(defaultValue = "0")
            @Min(0) @Max(ListPillsUseCase.MAX_PAGE) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(ListPillsUseCase.MAX_PAGE_SIZE) int size) {
        var result = listPills.list(page, size);
        return PagedResponse.of(
                result.pills().stream().map(mapper::toResponse).toList(),
                result.page(), result.size(), result.totalElements());
    }

    @PutMapping("/{id}")
    PillResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePillRequest request,
                        JwtAuthenticationToken authentication) {
        var pill = updatePill.update(mapper.toCommand(id, request), CallerMapper.from(authentication));
        return mapper.toResponse(pill);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        deletePill.delete(PillId.of(id), CallerMapper.from(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    PillResponse publish(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return mapper.toResponse(publishPill.publish(PillId.of(id), CallerMapper.from(authentication)));
    }
}

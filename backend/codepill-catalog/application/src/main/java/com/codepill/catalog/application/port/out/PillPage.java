package com.codepill.catalog.application.port.out;

import com.codepill.catalog.domain.Pill;

import java.util.List;

/** One page of the published pill feed. */
public record PillPage(List<Pill> pills, long totalElements, int page, int size) {

    public PillPage {
        pills = List.copyOf(pills);
    }
}

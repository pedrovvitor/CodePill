package com.codepill.catalog.domain;

/** Lifecycle states of a pill; mirrors the ck_pills_status database constraint. */
public enum PillStatus {
    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    ARCHIVED
}

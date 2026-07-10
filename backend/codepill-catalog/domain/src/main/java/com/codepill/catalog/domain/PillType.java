package com.codepill.catalog.domain;

/** Content formats a pill can take; mirrors the ck_pills_type database constraint. */
public enum PillType {
    ARTICLE,
    QUIZ,
    FLASHCARD,
    VIDEO
}

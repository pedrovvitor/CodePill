package com.codepill.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * codepill-catalog — pills, courses, tracks, authoring (ARCHITECTURE.md §3.2).
 * Rooted at {@code com.codepill.catalog} so component/entity/repository
 * scanning covers every adapter module.
 */
@SpringBootApplication
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}

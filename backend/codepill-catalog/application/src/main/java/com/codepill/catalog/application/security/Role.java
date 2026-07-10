package com.codepill.catalog.application.security;

/**
 * Platform roles, delivered in the JWT {@code roles} claim (SECURITY.md §3.1).
 * The IdP expands composite roles, so a CURATOR token also carries AUTHOR and
 * LEARNER; the resource server additionally declares a RoleHierarchy as
 * defense in depth.
 */
public enum Role {
    LEARNER,
    AUTHOR,
    CURATOR,
    ADMIN
}

package com.job.portal.monitoring;

/**
 * A visitor told us who they are: signed in, sent the contact form, or subscribed.
 *
 * @param visitorId anonymous browser id (may be null when unknown)
 * @param name      display name they provided (may be null)
 * @param email     email address (verified for sign-ins, self-declared for forms)
 * @param username  account username for sign-ins, else null
 * @param source    google | github | account | contact | newsletter
 */
public record VisitorIdentifiedEvent(String visitorId, String name, String email, String username, String source) {

    /** Sign-ins are stronger evidence than forms; a weaker source never overwrites a stronger one. */
    static int rank(String source) {
        if (source == null) return 0;
        return switch (source) {
            case "google", "github", "account" -> 3;
            case "contact" -> 2;
            case "newsletter" -> 1;
            default -> 0;
        };
    }
}

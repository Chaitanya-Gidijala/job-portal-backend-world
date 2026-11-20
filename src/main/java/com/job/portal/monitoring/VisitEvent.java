package com.job.portal.monitoring;

/**
 * Published by {@link PresenceService} on every visitor heartbeat.
 *
 * @param session    consistent snapshot of the live session
 * @param newSession first heartbeat of this tab session (a new "visit")
 * @param newPage    the visitor moved to a different page
 */
public record VisitEvent(PresenceService.LiveSession session, boolean newSession, boolean newPage) {
}

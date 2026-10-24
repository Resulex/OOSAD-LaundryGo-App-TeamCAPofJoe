package com.laundrygo.model;

import java.time.LocalDateTime;

/** One line of the audit trail shown on the owner dashboard. */
public record AuditEntry(long id, LocalDateTime at, String actor, String action, String detail) {
}

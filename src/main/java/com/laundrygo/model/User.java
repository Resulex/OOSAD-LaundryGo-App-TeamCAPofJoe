package com.laundrygo.model;

import java.util.Objects;

/** A signed-in staff or owner account. Passwords never live on this object. */
public final class User {

    private final long id;
    private final String username;
    private final String fullName;
    private final Role role;
    private final boolean active;

    public User(long id, String username, String fullName, Role role, boolean active) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.role = Objects.requireNonNull(role, "role");
        this.active = active;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}

package com.laundrygo.service;

import com.laundrygo.db.AuditDao;
import com.laundrygo.db.UserDao;
import com.laundrygo.model.User;
import java.util.Optional;

/** Credential verification. Runs on a background thread from the login screen. */
public final class AuthService {

    private final UserDao users;
    private final AuditDao audit;

    public AuthService(UserDao users, AuditDao audit) {
        this.users = users;
        this.audit = audit;
    }

    public Optional<User> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return Optional.empty();
        }
        Optional<UserDao.Credential> found = users.credentials(username.trim());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserDao.Credential credential = found.get();
        if (!credential.active() || !com.laundrygo.util.PasswordHasher.verify(
                password, credential.salt(), credential.hash())) {
            return Optional.empty();
        }
        User user = new User(credential.id(), credential.username(), credential.fullName(),
                credential.role(), true, credential.shift());
        audit.log(user.getUsername(), "LOGIN", user.getFullName() + " signed in as "
                + user.getRole().displayName());
        return Optional.of(user);
    }
}

package com.auth.authservice.user;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record User(
        @Id UUID id,
        String email,
        @Column("password_hash") String passwordHash,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt)
        implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }

    // Users are insert-only; UUIDs are assigned before persistence.
    @Override
    public boolean isNew() {
        return true;
    }
}

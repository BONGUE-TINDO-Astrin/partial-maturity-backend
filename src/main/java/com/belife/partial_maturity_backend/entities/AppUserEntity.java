package com.belife.partial_maturity_backend.entities;

import com.belife.partial_maturity_backend.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "app_user",
        schema = "partial_maturity",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_app_user_username", columnNames = "username")
        }
)
public class AppUserEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}

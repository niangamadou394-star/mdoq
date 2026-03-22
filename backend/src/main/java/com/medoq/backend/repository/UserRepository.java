package com.medoq.backend.repository;

import com.medoq.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    // ── Admin queries ──────────────────────────────────────────────

    @Query(value = """
        SELECT u FROM User u
        WHERE (:role   IS NULL OR u.role   = :role)
          AND (:status IS NULL OR u.status = :status)
          AND (:search IS NULL
               OR LOWER(u.phone)     LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY u.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(u) FROM User u
        WHERE (:role   IS NULL OR u.role   = :role)
          AND (:status IS NULL OR u.status = :status)
          AND (:search IS NULL
               OR LOWER(u.phone)     LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<User> findAdminList(
        @Param("role")   User.Role   role,
        @Param("status") User.Status status,
        @Param("search") String      search,
        Pageable pageable);

    long countByRole(User.Role role);

    long countByStatus(User.Status status);

    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :hash, u.updatedAt = CURRENT_TIMESTAMP WHERE u.phone = :phone")
    int updatePasswordByPhone(@Param("phone") String phone, @Param("hash") String hash);

    @Modifying
    @Query("UPDATE User u SET u.refreshToken = :token WHERE u.id = :id")
    int updateRefreshToken(@Param("id") UUID id, @Param("token") String token);

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") User.Status status);
}

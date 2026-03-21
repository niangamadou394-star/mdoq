package com.medoq.backend.repository;

import com.medoq.backend.entity.User;
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

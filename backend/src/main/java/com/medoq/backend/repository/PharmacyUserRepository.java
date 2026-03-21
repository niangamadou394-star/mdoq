package com.medoq.backend.repository;

import com.medoq.backend.entity.PharmacyUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PharmacyUserRepository extends JpaRepository<PharmacyUser, UUID> {

    boolean existsByPharmacyIdAndUserId(UUID pharmacyId, UUID userId);
}

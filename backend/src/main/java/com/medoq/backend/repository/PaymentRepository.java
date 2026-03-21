package com.medoq.backend.repository;

import com.medoq.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.reservation r
        JOIN FETCH r.customer
        JOIN FETCH r.pharmacy
        LEFT JOIN FETCH r.items i
        LEFT JOIN FETCH i.medication
        WHERE p.id = :id
        """)
    Optional<Payment> findByIdWithDetails(@Param("id") UUID id);

    Optional<Payment> findByTransactionRef(String transactionRef);

    Optional<Payment> findByProviderSession(String providerSession);

    List<Payment> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);

    /** Idempotency check: is this event already processed? */
    boolean existsByTransactionRef(String transactionRef);
}

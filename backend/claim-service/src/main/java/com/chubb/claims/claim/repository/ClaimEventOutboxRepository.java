package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.entity.ClaimEventOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimEventOutboxRepository extends JpaRepository<ClaimEventOutbox, Long> {

    /** The oldest waiting events first, so they are sent in the order they happened. */
    List<ClaimEventOutbox> findAllByOrderByIdAsc(Pageable pageable);
}

package com.prosto.analytics.repository;

import com.prosto.analytics.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findByDatasetIdAndUserIdOrderByUpdatedAtDesc(UUID datasetId, UUID userId);
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);
}

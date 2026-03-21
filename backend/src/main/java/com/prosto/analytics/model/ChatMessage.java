package com.prosto.analytics.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applied_config", columnDefinition = "jsonb")
    private String appliedConfig;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ChatSession getSession() { return session; }
    public void setSession(ChatSession session) { this.session = session; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getAppliedConfig() { return appliedConfig; }
    public void setAppliedConfig(String appliedConfig) { this.appliedConfig = appliedConfig; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

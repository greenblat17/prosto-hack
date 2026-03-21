package com.prosto.analytics.controller;

import com.prosto.analytics.dto.ChatMessageDto;
import com.prosto.analytics.dto.ChatSessionDto;
import com.prosto.analytics.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.prosto.analytics.dto.CreateChatSessionDto;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Tag(name = "Chat Sessions", description = "Управление чат-сессиями")
@RestController
@RequestMapping("/api/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @Operation(summary = "Список чат-сессий для датасета")
    @GetMapping
    public List<ChatSessionDto> listSessions(@RequestParam UUID datasetId, Authentication auth) {
        return chatSessionService.listSessions(datasetId, auth.getName());
    }

    @Operation(summary = "Создать новую чат-сессию")
    @PostMapping
    public ChatSessionDto createSession(@RequestBody @Valid CreateChatSessionDto body, Authentication auth) {
        return chatSessionService.createSession(body.datasetId(), auth.getName());
    }

    @Operation(summary = "Удалить чат-сессию")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id, Authentication auth) {
        chatSessionService.deleteSession(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Получить сообщения чат-сессии")
    @GetMapping("/{id}/messages")
    public List<ChatMessageDto> getMessages(@PathVariable UUID id, Authentication auth) {
        return chatSessionService.getMessages(id, auth.getName());
    }
}

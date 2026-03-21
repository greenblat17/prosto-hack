package com.prosto.analytics.service;

import com.prosto.analytics.dto.ChatMessageDto;
import com.prosto.analytics.dto.ChatSessionDto;
import com.prosto.analytics.model.ChatSession;
import com.prosto.analytics.model.Dataset;
import com.prosto.analytics.model.User;
import com.prosto.analytics.repository.ChatMessageRepository;
import com.prosto.analytics.repository.ChatSessionRepository;
import com.prosto.analytics.repository.DatasetRepository;
import com.prosto.analytics.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;

    public ChatSessionService(ChatSessionRepository sessionRepository,
                              ChatMessageRepository messageRepository,
                              DatasetRepository datasetRepository,
                              UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.datasetRepository = datasetRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatSessionDto> listSessions(UUID datasetId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        return sessionRepository.findByDatasetIdAndUserIdOrderByUpdatedAtDesc(datasetId, user.getId())
                .stream()
                .map(s -> new ChatSessionDto(s.getId(), s.getDataset().getId(), s.getTitle(),
                        s.getCreatedAt(), s.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ChatSessionDto createSession(UUID datasetId, String userEmail) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + datasetId));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        ChatSession session = new ChatSession();
        session.setDataset(dataset);
        session.setUser(user);
        sessionRepository.save(session);

        return new ChatSessionDto(session.getId(), datasetId, session.getTitle(),
                session.getCreatedAt(), session.getUpdatedAt());
    }

    @Transactional
    public void deleteSession(UUID sessionId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Session not found"));
        sessionRepository.delete(session);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UUID sessionId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new NoSuchElementException("Session not found"));
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(m -> new ChatMessageDto(m.getId(), m.getRole(), m.getText(),
                        m.getAppliedConfig(), m.getCreatedAt()))
                .toList();
    }
}

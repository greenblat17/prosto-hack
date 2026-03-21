package com.prosto.analytics.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateChatSessionDto(
        @NotNull UUID datasetId
) {}

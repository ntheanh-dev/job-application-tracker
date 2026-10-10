package com.local.jobtracker.model;

import java.time.LocalDateTime;

public record JobApplication(
        String id,
        String applyLink,
        int yearsOfExperience,
        String position,
        String cvOriginalName,
        String cvStoredName,
        String cvDirectory,
        LocalDateTime createdAt
) {}

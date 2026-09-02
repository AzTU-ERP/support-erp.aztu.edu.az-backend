package com.aztu.support_erp.violation.dto;

import com.aztu.support_erp.user.dto.UserMapper;
import com.aztu.support_erp.violation.domain.UserViolation;

public final class ViolationMapper {
    private ViolationMapper() {}

    public static ViolationResponse toResponse(UserViolation v) {
        return new ViolationResponse(
                v.getId(),
                UserMapper.toSummary(v.getUser()),
                v.getIrrelevantCount(),
                v.getWarnedAt() != null,
                v.getWarnedAt(),
                v.isBlocked(),
                v.getBlockedAt(),
                UserMapper.toSummary(v.getBlockedByDev()),
                v.getUnblockedAt(),
                UserMapper.toSummary(v.getUnblockedByDev()),
                v.getUpdatedAt());
    }
}

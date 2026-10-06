package com.kayogx.eventcard.dto;

import com.kayogx.eventcard.model.EventStatus;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(

        @NotNull(message = "Please choose the new status")
        EventStatus status
) {
}

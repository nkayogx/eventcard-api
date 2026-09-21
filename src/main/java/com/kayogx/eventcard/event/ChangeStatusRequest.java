package com.kayogx.eventcard.event;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(

        @NotNull(message = "Please choose the new status")
        EventStatus status
) {
}

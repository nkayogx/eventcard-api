package com.kayogx.eventcard.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The shape of every error the API sends back, for example:
 * { "message": "This email is already registered", "field": "email" }
 *
 * "field" names the form input the error is about, and is left out
 * when the error is not about one specific input.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, String field) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, null);
    }
}

package com.dotblog.shared.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, String code) {
    public ErrorResponse(String message) {
        this(message, null);
    }
}

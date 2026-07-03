package com.jobscheduler.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private List<FieldError> fieldErrors;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}

package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaveCardRequest(
        Integer rating,
        String review,
        String status
) {}

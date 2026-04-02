package com.ecm.core.dto;

public record CreateCommentRequest(
    String content,
    String parentCommentId
) {
}

package com.ecm.core.dto;

import com.ecm.core.entity.Node;
import com.ecm.core.model.Comment;
import com.ecm.core.service.CommentService;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public record CommentDto(
    UUID id,
    String content,
    String author,
    UUID nodeId,
    String nodeName,
    String nodeType,
    String created,
    String edited,
    String editor,
    int level,
    List<CommentReactionDto> reactions,
    List<String> mentionedUsers,
    List<CommentDto> replies
) {
    public static CommentDto from(Comment comment) {
        if (comment == null) {
            return null;
        }

        return new CommentDto(
            comment.getId(),
            comment.getContent(),
            comment.getAuthor(),
            comment.getNode() != null ? comment.getNode().getId() : null,
            comment.getNode() != null ? comment.getNode().getName() : null,
            comment.getNode() != null && comment.getNode().getNodeType() != null
                ? comment.getNode().getNodeType().name()
                : null,
            formatDate(comment.getCreated()),
            formatDate(comment.getEdited()),
            comment.getEditor(),
            comment.getLevel() != null ? comment.getLevel() : 0,
            comment.getReactions() == null ? List.of() : comment.getReactions().stream()
                .map(CommentReactionDto::from)
                .toList(),
            comment.getMentionedUsers() == null ? List.of() : comment.getMentionedUsers().stream()
                .sorted()
                .toList(),
            comment.getReplies() == null ? List.of() : comment.getReplies().stream()
                .filter(reply -> reply != null && !Boolean.TRUE.equals(reply.getDeleted()))
                .map(CommentDto::from)
                .toList()
        );
    }

    public static CommentDto from(CommentService.CommentTreeNode treeNode) {
        if (treeNode == null) {
            return null;
        }

        Comment comment = treeNode.getComment();
        List<CommentDto> replies = treeNode.getReplies() == null ? List.of() : treeNode.getReplies().stream()
            .map(CommentDto::from)
            .toList();

        return new CommentDto(
            comment.getId(),
            comment.getContent(),
            comment.getAuthor(),
            comment.getNode() != null ? comment.getNode().getId() : null,
            comment.getNode() != null ? comment.getNode().getName() : null,
            comment.getNode() != null && comment.getNode().getNodeType() != null
                ? comment.getNode().getNodeType().name()
                : null,
            formatDate(comment.getCreated()),
            formatDate(comment.getEdited()),
            comment.getEditor(),
            comment.getLevel() != null ? comment.getLevel() : 0,
            comment.getReactions() == null ? List.of() : comment.getReactions().stream()
                .map(CommentReactionDto::from)
                .toList(),
            comment.getMentionedUsers() == null ? List.of() : comment.getMentionedUsers().stream()
                .sorted()
                .toList(),
            replies
        );
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(date.getTime()).atZone(ZoneOffset.UTC));
    }
}

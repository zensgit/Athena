package com.ecm.core.dto;

import com.ecm.core.model.Comment;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public record CommentReactionDto(
    String type,
    String user,
    String date
) {
    public static CommentReactionDto from(Comment.Reaction reaction) {
        if (reaction == null) {
            return null;
        }

        return new CommentReactionDto(
            reaction.getType(),
            reaction.getUser(),
            formatDate(reaction.getDate())
        );
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(date.getTime()).atZone(ZoneOffset.UTC));
    }
}

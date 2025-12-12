package com.ecm.core.event;

import com.ecm.core.model.Comment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommentAddedEvent extends ApplicationEvent {
    private final Comment comment;

    public CommentAddedEvent(Comment comment) {
        super(comment);
        this.comment = comment;
    }
}

package com.ecm.core.model;

import com.ecm.core.entity.Node;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL)
    @OrderBy("created ASC")
    private List<Comment> replies = new ArrayList<>();

    @Column(name = "created_date", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String author;

    @Column(name = "edited", nullable = false)
    private Boolean editedFlag = false;

    @Column(name = "edited_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date edited;

    @Column(name = "last_modified_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastModified;

    @Column(name = "last_modified_by")
    private String editor;

    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted = false;

    @ElementCollection
    @CollectionTable(name = "comment_mentions", joinColumns = @JoinColumn(name = "comment_id"))
    @Column(name = "mentioned_user")
    private Set<String> mentionedUsers = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "comment_reactions", joinColumns = @JoinColumn(name = "comment_id"))
    @OrderColumn(name = "reactions_order")
    private List<Reaction> reactions = new ArrayList<>();

    @Column(name = "level", nullable = false)
    private Integer level = 0;

    @PrePersist
    protected void onCreate() {
        Date now = new Date();
        if (created == null) {
            created = now;
        }
        if (lastModified == null) {
            lastModified = now;
        }
        if (parentComment != null) {
            level = parentComment.getLevel() + 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        edited = new Date();
        editedFlag = true;
        lastModified = edited;
    }

    // Nested class for reactions
    @Embeddable
    public static class Reaction {
        @Column(name = "reaction_type")
        private String type; // like, thumbsup, thumbsdown, heart, etc.

        @Column(name = "reaction_user")
        private String user;

        @Column(name = "reaction_date")
        @Temporal(TemporalType.TIMESTAMP)
        private Date date;

        // Constructors
        public Reaction() {}

        public Reaction(String type, String user) {
            this.type = type;
            this.user = user;
            this.date = new Date();
        }

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }

        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
    }

    // Methods
    public void addReaction(String type, String user) {
        // Remove existing reaction from same user
        reactions.removeIf(r -> r.getUser().equals(user));
        // Add new reaction
        reactions.add(new Reaction(type, user));
    }

    public void removeReaction(String user) {
        reactions.removeIf(r -> r.getUser().equals(user));
    }

    public Map<String, Integer> getReactionCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Reaction reaction : reactions) {
            counts.merge(reaction.getType(), 1, Integer::sum);
        }
        return counts;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Node getNode() { return node; }
    public void setNode(Node node) { this.node = node; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Comment getParentComment() { return parentComment; }
    public void setParentComment(Comment parentComment) { 
        this.parentComment = parentComment;
        if (parentComment != null) {
            this.level = parentComment.getLevel() + 1;
        }
    }

    public List<Comment> getReplies() { return replies; }
    public void setReplies(List<Comment> replies) { this.replies = replies; }

    public Date getCreated() { return created; }
    public void setCreated(Date created) { this.created = created; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Boolean getEditedFlag() { return editedFlag; }
    public void setEditedFlag(Boolean editedFlag) { this.editedFlag = editedFlag; }

    public Date getEdited() { return edited; }
    public void setEdited(Date edited) { this.edited = edited; }

    public Date getLastModified() { return lastModified; }
    public void setLastModified(Date lastModified) { this.lastModified = lastModified; }

    public String getEditor() { return editor; }
    public void setEditor(String editor) { this.editor = editor; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public Set<String> getMentionedUsers() { return mentionedUsers; }
    public void setMentionedUsers(Set<String> mentionedUsers) { 
        this.mentionedUsers = mentionedUsers; 
    }

    public List<Reaction> getReactions() { return reactions; }
    public void setReactions(List<Reaction> reactions) { this.reactions = reactions; }

    public Integer getLevel() { return level; }
}

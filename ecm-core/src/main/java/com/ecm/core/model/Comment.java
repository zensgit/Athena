package com.ecm.core.model;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "ecm_comments")
public class Comment {
    @Id
    @GeneratedValue(generator = "uuid2")
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;
    
    @Column(nullable = false, length = 2000)
    private String content;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;
    
    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL)
    @OrderBy("created ASC")
    private List<Comment> replies = new ArrayList<>();
    
    @Column(nullable = false)
    private Date created;
    
    @Column(nullable = false)
    private String author;
    
    @Column
    private Date edited;
    
    @Column
    private String editor;
    
    @Column(nullable = false)
    private Boolean deleted = false;
    
    @ElementCollection
    @CollectionTable(name = "comment_mentions", joinColumns = @JoinColumn(name = "comment_id"))
    @Column(name = "mentioned_user")
    private Set<String> mentionedUsers = new HashSet<>();
    
    @ElementCollection
    @CollectionTable(name = "comment_reactions", joinColumns = @JoinColumn(name = "comment_id"))
    private List<Reaction> reactions = new ArrayList<>();
    
    @Column(nullable = false)
    private Integer level = 0;
    
    @PrePersist
    protected void onCreate() {
        created = new Date();
        if (parentComment != null) {
            level = parentComment.getLevel() + 1;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        edited = new Date();
    }
    
    // Nested class for reactions
    @Embeddable
    public static class Reaction {
        @Column(name = "reaction_type")
        private String type; // like, thumbsup, thumbsdown, heart, etc.
        
        @Column(name = "reaction_user")
        private String user;
        
        @Column(name = "reaction_date")
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
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
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
    
    public Date getEdited() { return edited; }
    public void setEdited(Date edited) { this.edited = edited; }
    
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
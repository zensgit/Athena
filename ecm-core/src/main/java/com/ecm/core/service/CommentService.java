package com.ecm.core.service;

import com.ecm.core.model.*;
import com.ecm.core.repository.*;
import com.ecm.core.exception.*;
import com.ecm.core.event.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommentService {
    
    @Autowired
    private CommentRepository commentRepository;
    
    @Autowired
    private NodeRepository nodeRepository;
    
    @Autowired
    private SecurityService securityService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.-]+)");
    private static final int MAX_COMMENT_DEPTH = 5;
    
    /**
     * 添加评论
     */
    public Comment addComment(String nodeId, String content, String parentCommentId) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.READ);
        
        Comment comment = new Comment();
        comment.setNode(node);
        comment.setContent(content);
        comment.setAuthor(securityService.getCurrentUser());
        
        // 设置父评论
        if (parentCommentId != null) {
            Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
            
            // 检查评论深度
            if (parentComment.getLevel() >= MAX_COMMENT_DEPTH) {
                throw new IllegalOperationException("Maximum comment depth exceeded");
            }
            
            comment.setParentComment(parentComment);
        }
        
        // 提取并处理@提及
        Set<String> mentionedUsers = extractMentions(content);
        comment.setMentionedUsers(mentionedUsers);
        
        comment = commentRepository.save(comment);
        
        // 发送通知
        notifyMentionedUsers(comment, mentionedUsers);
        
        // 通知文档所有者
        if (!comment.getAuthor().equals(node.getCreator())) {
            notificationService.notifyUser(
                node.getCreator(),
                "New comment on your document",
                String.format("%s commented on '%s'", comment.getAuthor(), node.getName())
            );
        }
        
        // 发布事件
        eventPublisher.publishEvent(new CommentAddedEvent(comment));
        
        return comment;
    }
    
    /**
     * 获取节点的评论
     */
    public Page<Comment> getNodeComments(String nodeId, Pageable pageable) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.READ);
        
        // 只获取顶级评论，回复通过嵌套加载
        return commentRepository.findByNodeAndParentCommentIsNullAndDeletedFalseOrderByCreatedDesc(
            node, pageable);
    }
    
    /**
     * 获取评论树
     */
    public List<CommentTreeNode> getCommentTree(String nodeId) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.READ);
        
        List<Comment> rootComments = commentRepository
            .findByNodeAndParentCommentIsNullAndDeletedFalseOrderByCreatedDesc(node);
        
        return buildCommentTree(rootComments);
    }
    
    private List<CommentTreeNode> buildCommentTree(List<Comment> comments) {
        return comments.stream()
            .map(comment -> {
                CommentTreeNode node = new CommentTreeNode();
                node.setComment(comment);
                
                if (!comment.getReplies().isEmpty()) {
                    List<Comment> activeReplies = comment.getReplies().stream()
                        .filter(reply -> !reply.getDeleted())
                        .collect(Collectors.toList());
                    node.setReplies(buildCommentTree(activeReplies));
                }
                
                return node;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 编辑评论
     */
    public Comment editComment(String commentId, String newContent) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        
        // 只有作者可以编辑
        if (!comment.getAuthor().equals(securityService.getCurrentUser())) {
            throw new AccessDeniedException("Only the author can edit this comment");
        }
        
        comment.setContent(newContent);
        comment.setEditor(securityService.getCurrentUser());
        
        // 更新提及
        Set<String> newMentions = extractMentions(newContent);
        Set<String> oldMentions = new HashSet<>(comment.getMentionedUsers());
        comment.setMentionedUsers(newMentions);
        
        comment = commentRepository.save(comment);
        
        // 通知新提及的用户
        newMentions.removeAll(oldMentions);
        notifyMentionedUsers(comment, newMentions);
        
        return comment;
    }
    
    /**
     * 删除评论（软删除）
     */
    public void deleteComment(String commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        
        // 只有作者或管理员可以删除
        String currentUser = securityService.getCurrentUser();
        if (!comment.getAuthor().equals(currentUser) && 
            !securityService.isAdmin(currentUser)) {
            throw new AccessDeniedException("You don't have permission to delete this comment");
        }
        
        comment.setDeleted(true);
        commentRepository.save(comment);
        
        // 软删除所有回复
        deleteRepliesRecursive(comment);
    }
    
    private void deleteRepliesRecursive(Comment comment) {
        for (Comment reply : comment.getReplies()) {
            reply.setDeleted(true);
            commentRepository.save(reply);
            deleteRepliesRecursive(reply);
        }
    }
    
    /**
     * 添加反应
     */
    public void addReaction(String commentId, String reactionType) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        
        String currentUser = securityService.getCurrentUser();
        comment.addReaction(reactionType, currentUser);
        
        commentRepository.save(comment);
        
        // 通知评论作者
        if (!currentUser.equals(comment.getAuthor())) {
            notificationService.notifyUser(
                comment.getAuthor(),
                "New reaction on your comment",
                String.format("%s reacted with %s to your comment", currentUser, reactionType)
            );
        }
    }
    
    /**
     * 移除反应
     */
    public void removeReaction(String commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        
        comment.removeReaction(securityService.getCurrentUser());
        commentRepository.save(comment);
    }
    
    /**
     * 获取用户的评论
     */
    public Page<Comment> getUserComments(String username, Pageable pageable) {
        return commentRepository.findByAuthorAndDeletedFalseOrderByCreatedDesc(username, pageable);
    }
    
    /**
     * 获取提及用户的评论
     */
    public Page<Comment> getMentionedComments(String username, Pageable pageable) {
        return commentRepository.findByMentionedUsersContainingAndDeletedFalse(username, pageable);
    }
    
    /**
     * 搜索评论
     */
    public List<Comment> searchComments(String nodeId, String query) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        // 权限检查
        securityService.checkPermission(node, Permission.READ);
        
        return commentRepository.findByNodeAndContentContainingIgnoreCaseAndDeletedFalse(
            node, query);
    }
    
    /**
     * 获取评论统计
     */
    public CommentStatistics getCommentStatistics(String nodeId) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NodeNotFoundException("Node not found: " + nodeId));
        
        CommentStatistics stats = new CommentStatistics();
        stats.setNodeId(nodeId);
        stats.setTotalComments(commentRepository.countByNodeAndDeletedFalse(node));
        stats.setUniqueCommenters(commentRepository.countUniqueCommenters(node));
        
        // 获取最活跃的评论者
        List<Object[]> topCommenters = commentRepository.findTopCommenters(node, 5);
        Map<String, Long> commentersMap = new LinkedHashMap<>();
        for (Object[] row : topCommenters) {
            commentersMap.put((String) row[0], (Long) row[1]);
        }
        stats.setTopCommenters(commentersMap);
        
        return stats;
    }
    
    // 辅助方法
    private Set<String> extractMentions(String content) {
        Set<String> mentions = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        
        return mentions;
    }
    
    private void notifyMentionedUsers(Comment comment, Set<String> mentionedUsers) {
        for (String username : mentionedUsers) {
            if (!username.equals(comment.getAuthor())) {
                notificationService.notifyUser(
                    username,
                    "You were mentioned in a comment",
                    String.format("%s mentioned you in a comment on '%s'", 
                                comment.getAuthor(), comment.getNode().getName())
                );
            }
        }
    }
    
    // 内部类
    public static class CommentTreeNode {
        private Comment comment;
        private List<CommentTreeNode> replies = new ArrayList<>();
        
        // Getters and setters
        public Comment getComment() { return comment; }
        public void setComment(Comment comment) { this.comment = comment; }
        
        public List<CommentTreeNode> getReplies() { return replies; }
        public void setReplies(List<CommentTreeNode> replies) { this.replies = replies; }
    }
    
    public static class CommentStatistics {
        private String nodeId;
        private Long totalComments;
        private Long uniqueCommenters;
        private Map<String, Long> topCommenters;
        
        // Getters and setters
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        
        public Long getTotalComments() { return totalComments; }
        public void setTotalComments(Long totalComments) { 
            this.totalComments = totalComments; 
        }
        
        public Long getUniqueCommenters() { return uniqueCommenters; }
        public void setUniqueCommenters(Long uniqueCommenters) { 
            this.uniqueCommenters = uniqueCommenters; 
        }
        
        public Map<String, Long> getTopCommenters() { return topCommenters; }
        public void setTopCommenters(Map<String, Long> topCommenters) { 
            this.topCommenters = topCommenters; 
        }
    }
}
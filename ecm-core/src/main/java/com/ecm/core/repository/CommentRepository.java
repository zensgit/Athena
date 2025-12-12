package com.ecm.core.repository;

import com.ecm.core.entity.Node;
import com.ecm.core.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByNodeAndParentCommentIsNullAndDeletedFalseOrderByCreatedDesc(
        Node node, Pageable pageable);

    List<Comment> findByNodeAndParentCommentIsNullAndDeletedFalseOrderByCreatedDesc(Node node);

    Page<Comment> findByAuthorAndDeletedFalseOrderByCreatedDesc(String author, Pageable pageable);

    Page<Comment> findByMentionedUsersContainingAndDeletedFalse(String username, Pageable pageable);

    List<Comment> findByNodeAndContentContainingIgnoreCaseAndDeletedFalse(Node node, String content);

    Long countByNodeAndDeletedFalse(Node node);

    @Query("SELECT COUNT(DISTINCT c.author) FROM Comment c WHERE c.node = ?1 AND c.deleted = false")
    Long countUniqueCommenters(Node node);

    @Query("SELECT c.author, COUNT(c) FROM Comment c WHERE c.node = ?1 AND c.deleted = false " +
           "GROUP BY c.author ORDER BY COUNT(c) DESC")
    List<Object[]> findTopCommenters(Node node, Pageable pageable);
}

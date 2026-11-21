package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaCommentEntity;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.NotificationType;
import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaRatingEntity;
import com.claySoftware.MathExpAssistant.exceptions.SocialAccessDeniedException;
import com.claySoftware.MathExpAssistant.exceptions.SocialResourceNotFoundException;
import com.claySoftware.MathExpAssistant.models.FormulaCommentRequest;
import com.claySoftware.MathExpAssistant.models.FormulaCommentUpdateRequest;
import com.claySoftware.MathExpAssistant.models.FormulaCommentResponse;
import com.claySoftware.MathExpAssistant.repositories.FormulaCommentRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaRatingRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.claySoftware.MathExpAssistant.models.FormulaStatus.PUBLIC;

@Service
public class FormulaSocialService {
    private static final int MAX_COMMENT_DEPTH = 5;

    private final FormulaRepository formulaRepository;
    private final FormulaRatingRepository ratingRepository;
    private final FormulaCommentRepository commentRepository;
    private final CommunityEvents events;
    private final PublicAuthorService authors;

    public FormulaSocialService(
            FormulaRepository formulaRepository,
            FormulaRatingRepository ratingRepository,
            FormulaCommentRepository commentRepository, CommunityEvents events, PublicAuthorService authors) {
        this.formulaRepository = formulaRepository;
        this.ratingRepository = ratingRepository;
        this.commentRepository = commentRepository;
        this.events = events;
        this.authors = authors;
    }

    public FormulaEntity rate(String formulaId, String userId, int value) {
        requireUser(userId);
        if (value != 1 && value != -1) {
            throw new IllegalArgumentException("rating value must be -1 or 1");
        }

        FormulaEntity formula = publicFormula(formulaId);
        if (userId.equals(formula.getOwnerUserId()) || userId.equals(formula.getOwnerEmail())
                || userId.equals(owner(formula))) {
            throw new SocialAccessDeniedException("formula owners cannot rate their own formulas");
        }

        FormulaRatingEntity rating = ratingRepository.findByFormulaIdAndUserId(formulaId, userId)
                .orElseGet(FormulaRatingEntity::new);
        if (rating.getCreatedAt() != null && rating.getValue() == value) {
            return formula;
        }
        events.limit("rating", userId, 100);
        boolean firstRating = rating.getCreatedAt() == null;
        rating.setFormulaId(formulaId);
        rating.setUserId(userId);
        rating.setValue(value);
        if (rating.getCreatedAt() == null) {
            rating.setCreatedAt(Instant.now());
        }
        rating.setUpdatedAt(Instant.now());
        ratingRepository.save(rating);
        if (firstRating) {
            events.notify(CommunityEvents.key(formulaId, userId), owner(formula), userId,
                    NotificationType.RATING, formula, null);
        }
        return refreshMetrics(formula);
    }

    public FormulaEntity removeRating(String formulaId, String userId) {
        requireUser(userId);
        FormulaEntity formula = publicFormula(formulaId);
        ratingRepository.findByFormulaIdAndUserId(formulaId, userId).filter(rating -> rating.getValue() != 0).ifPresent(rating -> {
            events.limit("rating", userId, 100);
            rating.setValue(0);
            rating.setUpdatedAt(Instant.now());
            ratingRepository.save(rating);
        });
        return refreshMetrics(formula);
    }

    public Page<FormulaCommentResponse> listComments(String formulaId, String viewer, Pageable pageable) {
        publicFormula(formulaId);
        return commentRepository
                .findAllByFormulaIdAndParentCommentIdIsNullAndDeletedFalse(formulaId, pageable)
                .map(comment -> toResponse(comment, viewer));
    }

    public Page<FormulaCommentResponse> listReplies(
            String formulaId, String parentCommentId, String viewer, Pageable pageable) {
        publicFormula(formulaId);
        FormulaCommentEntity parent = commentRepository.findById(parentCommentId)
                .filter(comment -> formulaId.equals(comment.getFormulaId()) && !comment.isDeleted())
                .orElseThrow(() -> new SocialResourceNotFoundException("parent comment not found"));
        return commentRepository
                .findAllByFormulaIdAndParentCommentIdAndDeletedFalse(formulaId, parent.getId(), pageable)
                .map(comment -> toResponse(comment, viewer));
    }

    private FormulaCommentResponse toResponse(FormulaCommentEntity comment, String viewer) {
        var author = authors.account(comment.getUserId());
        return new FormulaCommentResponse(
                comment.getId(),
                comment.getFormulaId(),
                author.map(UserEntity::getId).orElse(null),
                author.map(PublicAuthorService::name).orElse(null),
                comment.getUserId().equals(viewer),
                comment.getContent(),
                comment.getParentCommentId(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                commentRepository.countByFormulaIdAndParentCommentIdAndDeletedFalse(
                        comment.getFormulaId(), comment.getId()),
                List.of());
    }

    public FormulaCommentEntity addComment(String formulaId, String userId, FormulaCommentRequest request) {
        requireUser(userId);
        FormulaEntity formula = publicFormula(formulaId);
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("comment content is required");
        }

        String replyAuthor = null;
        if (request.parentCommentId() != null && !request.parentCommentId().isBlank()) {
            FormulaCommentEntity parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new SocialResourceNotFoundException("parent comment not found"));
            if (!formulaId.equals(parent.getFormulaId()) || parent.isDeleted()) {
                throw new IllegalArgumentException("parent comment does not belong to this formula");
            }
            replyAuthor = authors.recipient(parent.getUserId());
            if (commentDepth(parent) + 1 >= MAX_COMMENT_DEPTH) {
                throw new IllegalArgumentException(
                        "comment thread cannot be deeper than " + MAX_COMMENT_DEPTH + " levels");
            }
        }

        events.limit("comment", userId, 30);
        FormulaCommentEntity comment = new FormulaCommentEntity();
        comment.setFormulaId(formulaId);
        comment.setUserId(userId);
        comment.setContent(request.content().trim());
        comment.setParentCommentId(request.parentCommentId() == null || request.parentCommentId().isBlank()
                ? null : request.parentCommentId());
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(comment.getCreatedAt());
        FormulaCommentEntity saved = commentRepository.save(comment);

        refreshCommentCount(formulaId);
        events.notify(saved.getId(), owner(formula), userId, NotificationType.COMMENT, formula, null);
        events.notify(saved.getId(), replyAuthor, userId, NotificationType.COMMENT, formula, null);
        return saved;
    }

    private int commentDepth(FormulaCommentEntity comment) {
        int depth = 0;
        String currentParentId = comment.getParentCommentId();
        Set<String> visited = new java.util.HashSet<>();
        while (currentParentId != null && !currentParentId.isBlank()) {
            if (!visited.add(currentParentId)) {
                throw new IllegalArgumentException("comment thread contains a cycle");
            }
            FormulaCommentEntity parent = commentRepository.findById(currentParentId)
                    .orElseThrow(() -> new SocialResourceNotFoundException("parent comment not found"));
            depth++;
            currentParentId = parent.getParentCommentId();
        }
        return depth;
    }

    public FormulaCommentEntity updateComment(
            String commentId, String userId, FormulaCommentUpdateRequest request) {
        requireUser(userId);
        FormulaCommentEntity comment = commentRepository.findById(commentId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new SocialResourceNotFoundException("comment not found"));
        if (!userId.equals(comment.getUserId())) {
            throw new SocialAccessDeniedException("only the comment author can edit it");
        }
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("comment content is required");
        }
        publicFormula(comment.getFormulaId());
        events.limit("comment", userId, 30);
        comment.setContent(request.content().trim());
        comment.setUpdatedAt(Instant.now());
        return commentRepository.save(comment);
    }

    public void deleteComment(String commentId, String userId) {
        requireUser(userId);
        FormulaCommentEntity comment = commentRepository.findById(commentId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new SocialResourceNotFoundException("comment not found"));
        if (!userId.equals(comment.getUserId())) {
            throw new SocialAccessDeniedException("only the comment author can delete it");
        }
        comment.setDeleted(true);
        comment.setUpdatedAt(Instant.now());
        commentRepository.save(comment);

        refreshCommentCount(comment.getFormulaId());
    }

    private void refreshCommentCount(String formulaId) {
        formulaRepository.updateCommentCount(formulaId,
                commentRepository.countByFormulaIdAndDeletedFalse(formulaId), Instant.now());
    }

    private FormulaEntity publicFormula(String formulaId) {
        return formulaRepository.findByIdAndStatus(formulaId, PUBLIC)
                .orElseThrow(() -> new SocialResourceNotFoundException("public formula not found"));
    }

    private FormulaEntity refreshMetrics(FormulaEntity formula) {
        long upvotes = ratingRepository.countByFormulaIdAndValue(formula.getId(), 1);
        long downvotes = ratingRepository.countByFormulaIdAndValue(formula.getId(), -1);
        long total = upvotes + downvotes;
        formula.setUpvotes(upvotes);
        formula.setDownvotes(downvotes);
        formula.setRatingCount(total);
        formula.setAverageRating(total == 0 ? 0 : (double) (upvotes - downvotes) / total);
        formula.setCommentCount(commentRepository.countByFormulaIdAndDeletedFalse(formula.getId()));
        formula.setUpdatedAt(Instant.now());
        formulaRepository.updateMetrics(formula.getId(), upvotes, downvotes, total, formula.getAverageRating(),
                formula.getCommentCount(), formula.getUpdatedAt());
        return formula;
    }

    private String owner(FormulaEntity formula) {
        return authors.formulaRecipient(formula);
    }

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new SocialAccessDeniedException("authenticated user is required");
        }
    }
}
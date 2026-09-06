package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaCommentEntity;
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

    public FormulaSocialService(
            FormulaRepository formulaRepository,
            FormulaRatingRepository ratingRepository,
            FormulaCommentRepository commentRepository) {
        this.formulaRepository = formulaRepository;
        this.ratingRepository = ratingRepository;
        this.commentRepository = commentRepository;
    }

    public FormulaEntity rate(String formulaId, String userId, int value) {
        requireUser(userId);
        if (value != 1 && value != -1) {
            throw new IllegalArgumentException("rating value must be -1 or 1");
        }

        FormulaEntity formula = publicFormula(formulaId);
        if (userId.equals(formula.getOwnerUserId())) {
            throw new SocialAccessDeniedException("formula owners cannot rate their own formulas");
        }

        FormulaRatingEntity rating = ratingRepository.findByFormulaIdAndUserId(formulaId, userId)
                .orElseGet(FormulaRatingEntity::new);
        rating.setFormulaId(formulaId);
        rating.setUserId(userId);
        rating.setValue(value);
        if (rating.getCreatedAt() == null) {
            rating.setCreatedAt(Instant.now());
        }
        rating.setUpdatedAt(Instant.now());
        ratingRepository.save(rating);
        return refreshMetrics(formula);
    }

    public FormulaEntity removeRating(String formulaId, String userId) {
        requireUser(userId);
        FormulaEntity formula = publicFormula(formulaId);
        ratingRepository.findByFormulaIdAndUserId(formulaId, userId)
                .ifPresent(ratingRepository::delete);
        return refreshMetrics(formula);
    }

    public Page<FormulaCommentResponse> listComments(String formulaId, Pageable pageable) {
        publicFormula(formulaId);
        return commentRepository
                .findAllByFormulaIdAndParentCommentIdIsNullAndDeletedFalse(formulaId, pageable)
                .map(this::toResponse);
    }

    public Page<FormulaCommentResponse> listReplies(
            String formulaId, String parentCommentId, Pageable pageable) {
        publicFormula(formulaId);
        FormulaCommentEntity parent = commentRepository.findById(parentCommentId)
                .filter(comment -> formulaId.equals(comment.getFormulaId()) && !comment.isDeleted())
                .orElseThrow(() -> new SocialResourceNotFoundException("parent comment not found"));
        return commentRepository
                .findAllByFormulaIdAndParentCommentIdAndDeletedFalse(formulaId, parent.getId(), pageable)
                .map(this::toResponse);
    }

    private FormulaCommentResponse toResponse(FormulaCommentEntity comment) {
        return new FormulaCommentResponse(
                comment.getId(),
                comment.getFormulaId(),
                comment.getUserId(),
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

        if (request.parentCommentId() != null && !request.parentCommentId().isBlank()) {
            FormulaCommentEntity parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new SocialResourceNotFoundException("parent comment not found"));
            if (!formulaId.equals(parent.getFormulaId()) || parent.isDeleted()) {
                throw new IllegalArgumentException("parent comment does not belong to this formula");
            }
            if (commentDepth(parent) + 1 >= MAX_COMMENT_DEPTH) {
                throw new IllegalArgumentException(
                        "comment thread cannot be deeper than " + MAX_COMMENT_DEPTH + " levels");
            }
        }

        FormulaCommentEntity comment = new FormulaCommentEntity();
        comment.setFormulaId(formulaId);
        comment.setUserId(userId);
        comment.setContent(request.content().trim());
        comment.setParentCommentId(request.parentCommentId());
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(comment.getCreatedAt());
        FormulaCommentEntity saved = commentRepository.save(comment);

        formula.setCommentCount(commentRepository.countByFormulaIdAndDeletedFalse(formulaId));
        formula.setUpdatedAt(Instant.now());
        formulaRepository.save(formula);
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

        formulaRepository.findByIdAndStatus(comment.getFormulaId(), PUBLIC).ifPresent(formula -> {
            formula.setCommentCount(commentRepository.countByFormulaIdAndDeletedFalse(comment.getFormulaId()));
            formula.setUpdatedAt(Instant.now());
            formulaRepository.save(formula);
        });
    }

    private FormulaEntity publicFormula(String formulaId) {
        return formulaRepository.findByIdAndStatus(formulaId, PUBLIC)
                .orElseThrow(() -> new SocialResourceNotFoundException("public formula not found"));
    }

    private FormulaEntity refreshMetrics(FormulaEntity formula) {
        long upvotes = ratingRepository.countByFormulaIdAndValue(formula.getId(), 1);
        long downvotes = ratingRepository.countByFormulaIdAndValue(formula.getId(), -1);
        long total = ratingRepository.countByFormulaId(formula.getId());
        formula.setUpvotes(upvotes);
        formula.setDownvotes(downvotes);
        formula.setRatingCount(total);
        formula.setAverageRating(total == 0 ? 0 : (double) (upvotes - downvotes) / total);
        formula.setCommentCount(commentRepository.countByFormulaIdAndDeletedFalse(formula.getId()));
        formula.setUpdatedAt(Instant.now());
        return formulaRepository.save(formula);
    }

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new SocialAccessDeniedException("authenticated user is required");
        }
    }
}
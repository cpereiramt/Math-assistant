package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaCommentEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaRatingEntity;
import com.claySoftware.MathExpAssistant.exceptions.SocialAccessDeniedException;
import com.claySoftware.MathExpAssistant.models.FormulaCommentRequest;
import com.claySoftware.MathExpAssistant.models.FormulaCommentUpdateRequest;
import com.claySoftware.MathExpAssistant.models.FormulaCommentResponse;
import com.claySoftware.MathExpAssistant.repositories.FormulaCommentRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaRatingRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FormulaSocialServiceTest {
    private final FormulaRepository formulaRepository = mock(FormulaRepository.class);
    private final FormulaRatingRepository ratingRepository = mock(FormulaRatingRepository.class);
    private final FormulaCommentRepository commentRepository = mock(FormulaCommentRepository.class);
    private final FormulaSocialService service = new FormulaSocialService(
            formulaRepository, ratingRepository, commentRepository);

    private FormulaEntity formula;

    @BeforeEach
    void setUp() {
        formula = new FormulaEntity();
        formula.setId("formula-1");
        formula.setOwnerUserId("owner@example.com");
        when(formulaRepository.findByIdAndStatus(eq("formula-1"), any())).thenReturn(Optional.of(formula));
        when(ratingRepository.findByFormulaIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(ratingRepository.countByFormulaIdAndValue(any(), anyInt())).thenReturn(0L);
        when(ratingRepository.countByFormulaId(any())).thenReturn(0L);
        when(commentRepository.countByFormulaIdAndDeletedFalse(any())).thenReturn(0L);
        when(ratingRepository.save(any(FormulaRatingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(formulaRepository.save(any(FormulaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsRatingsOutsidePositiveOrNegativeOne() {
        assertThatThrownBy(() -> service.rate("formula-1", "user@example.com", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1 or 1");

        verifyNoInteractions(ratingRepository);
    }

    @Test
    void preventsOwnerFromRatingOwnFormula() {
        assertThatThrownBy(() -> service.rate("formula-1", "owner@example.com", 1))
                .isInstanceOf(SocialAccessDeniedException.class);

        verifyNoInteractions(ratingRepository);
    }

    @Test
    void recalculatesCountersFromStoredRatings() {
        when(ratingRepository.countByFormulaIdAndValue("formula-1", 1)).thenReturn(3L);
        when(ratingRepository.countByFormulaIdAndValue("formula-1", -1)).thenReturn(1L);
        when(ratingRepository.countByFormulaId("formula-1")).thenReturn(4L);

        FormulaEntity result = service.rate("formula-1", "user@example.com", 1);

        assertThat(result.getUpvotes()).isEqualTo(3);
        assertThat(result.getDownvotes()).isEqualTo(1);
        assertThat(result.getRatingCount()).isEqualTo(4);
        assertThat(result.getAverageRating()).isEqualTo(0.5);
    }

    @Test
    void rejectsCommentReplyFromAnotherFormula() {
        FormulaCommentEntity parent = new FormulaCommentEntity();
        parent.setFormulaId("another-formula");
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.addComment(
                "formula-1", "user@example.com", new FormulaCommentRequest("reply", "comment-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void onlyCommentAuthorCanEditComment() {
        FormulaCommentEntity comment = new FormulaCommentEntity();
        comment.setFormulaId("formula-1");
        comment.setUserId("author@example.com");
        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.updateComment(
                "comment-1", "other@example.com", new FormulaCommentUpdateRequest("changed")))
                .isInstanceOf(SocialAccessDeniedException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void returnsRepliesNestedUnderTheirRootComment() {
        FormulaCommentEntity root = comment("root", null, "first@example.com", "Root comment");
        FormulaCommentEntity reply = comment("reply", "root", "second@example.com", "Reply comment");
        when(commentRepository.findAllByFormulaIdAndParentCommentIdIsNullAndDeletedFalse(
                eq("formula-1"), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(root)));
        when(commentRepository.countByFormulaIdAndParentCommentIdAndDeletedFalse("formula-1", "root"))
                .thenReturn(1L);

        var page = service.listComments("formula-1", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        FormulaCommentResponse response = page.getContent().get(0);
        assertThat(response.id()).isEqualTo("root");
        assertThat(response.replyCount()).isEqualTo(1);
        assertThat(response.replies()).isEmpty();

        when(commentRepository.findById("root")).thenReturn(Optional.of(root));
        when(commentRepository.findAllByFormulaIdAndParentCommentIdAndDeletedFalse(
                eq("formula-1"), eq("root"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(reply)));
        when(commentRepository.countByFormulaIdAndParentCommentIdAndDeletedFalse("formula-1", "reply"))
                .thenReturn(0L);

        var replies = service.listReplies("formula-1", "root", PageRequest.of(0, 20));
        assertThat(replies.getContent()).extracting(FormulaCommentResponse::id).containsExactly("reply");
    }

    @Test
    void rejectsRepliesBeyondMaximumDepth() {
        FormulaCommentEntity parent = comment("level-5", "level-4", "user@example.com", "Deep comment");
        when(commentRepository.findById("level-5")).thenReturn(Optional.of(parent));
        when(commentRepository.findById("level-4")).thenReturn(Optional.of(
                comment("level-4", "level-3", "user@example.com", "Deep comment")));
        when(commentRepository.findById("level-3")).thenReturn(Optional.of(
                comment("level-3", "level-2", "user@example.com", "Deep comment")));
        when(commentRepository.findById("level-2")).thenReturn(Optional.of(
                comment("level-2", "level-1", "user@example.com", "Deep comment")));
        when(commentRepository.findById("level-1")).thenReturn(Optional.of(
                comment("level-1", null, "user@example.com", "Deep comment")));

        assertThatThrownBy(() -> service.addComment(
                "formula-1", "user@example.com", new FormulaCommentRequest("too deep", "level-5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deeper than 5");
    }

    private FormulaCommentEntity comment(String id, String parentId, String userId, String content) {
        FormulaCommentEntity comment = new FormulaCommentEntity();
        comment.setId(id);
        comment.setFormulaId("formula-1");
        comment.setParentCommentId(parentId);
        comment.setUserId(userId);
        comment.setContent(content);
        return comment;
    }
}
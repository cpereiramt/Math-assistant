package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import java.time.Instant;

import java.util.List;
import java.util.Optional;

public interface FormulaRepository extends MongoRepository<FormulaEntity, String>, FormulaSearchRepository {
        Optional<FormulaEntity> findByName(String name);

        Optional<FormulaEntity> findByNameAndStatus(String name, FormulaStatus status);
        boolean existsByNameAndStatusAndIdNot(String name, FormulaStatus status, String id);

        Optional<FormulaEntity> findByIdAndStatus(String id, FormulaStatus status);

        Optional<FormulaEntity> findByNameAndOwnerUserIdAndStatus(
                        String name,
                        String ownerUserId,
                        FormulaStatus status);

        Optional<FormulaEntity> findByNameAndGroupAndOwnerUserIdAndStatus(
                        String name,
                        FormulaGroup group,
                        String ownerUserId,
                        FormulaStatus status);

        Optional<FormulaEntity> findByIdAndOwnerUserIdAndStatus(String id, String ownerUserId, FormulaStatus status);

        List<FormulaEntity> findAllByStatus(FormulaStatus status);

        List<FormulaEntity> findAllByStatusOrderByUpvotesDescAverageRatingDescCreatedAtDesc(FormulaStatus status);

        List<FormulaEntity> findAllByOwnerUserId(String ownerUserId);

        Optional<FormulaEntity> findByIdAndOwnerUserId(String id, String ownerUserId);

        @Query("{'_id': ?0, 'status': 'PUBLIC'}")
        @Update("{'$set': {'commentCount': ?1, 'updatedAt': ?2}}")
        void updateCommentCount(String id, long count, Instant updatedAt);

        @Query("{'_id': ?0, 'status': 'PUBLIC'}")
        @Update("{'$set': {'upvotes': ?1, 'downvotes': ?2, 'ratingCount': ?3, 'averageRating': ?4, 'commentCount': ?5, 'updatedAt': ?6}}")
        void updateMetrics(String id, long upvotes, long downvotes, long total, double average,
                           long commentCount, Instant updatedAt);
}

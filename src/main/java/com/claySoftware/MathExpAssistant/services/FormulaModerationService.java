package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaVersionStore;
import com.claySoftware.MathExpAssistant.repositories.FormulaWrites;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.NotificationType;
import com.claySoftware.MathExpAssistant.repositories.CommunityTransactions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class FormulaModerationService {
    private final FormulaRepository formulas;
    private final FormulaWrites writes;
    private final FormulaVersionStore versions;
    private final FormulaService formulaService;
    private final CommunityTransactions transactions;
    private final CommunityEvents events;
    private final PublicAuthorService authors;

    public FormulaModerationService(FormulaRepository formulas, FormulaWrites writes, FormulaVersionStore versions,
                                    FormulaService formulaService, CommunityTransactions transactions,
                                    CommunityEvents events, PublicAuthorService authors) {
        this.formulas = formulas;
        this.writes = writes;
        this.versions = versions;
        this.formulaService = formulaService;
        this.transactions = transactions;
        this.events = events;
        this.authors = authors;
    }

    public List<FormulaEntity> pending() {
        return formulas.findAllByStatus(FormulaStatus.UNDER_REVIEW);
    }

    public FormulaEntity approve(String id, String reviewer, String reason) {
        String justification = justification(reason);
        return transactions.write(() -> {
            FormulaEntity formula = requireStatus(id, FormulaStatus.UNDER_REVIEW);
            requireValidContent(formula);
            requireAvailableName(formula);
            review(formula, FormulaStatus.PUBLIC, reviewer, justification);
            authors.formulaAccount(formula).ifPresent(user -> formula.setAuthorId(user.getId()));
            formula.setVersion(1);
            formula.setPublicationKey(formula.getName());
            writes.replaceFromStatus(formula, FormulaStatus.UNDER_REVIEW);
            versions.append(formula);
            notifyReview(formula, NotificationType.APPROVED);
            return formula;
        });
    }

    public FormulaEntity reject(String id, String reviewer, String reason) {
        String justification = justification(reason);
        return transactions.write(() -> {
            FormulaEntity formula = requireStatus(id, FormulaStatus.UNDER_REVIEW);
            review(formula, FormulaStatus.REJECTED, reviewer, justification);
            writes.replaceFromStatus(formula, FormulaStatus.UNDER_REVIEW);
            notifyReview(formula, NotificationType.REJECTED);
            return formula;
        });
    }

    private void notifyReview(FormulaEntity formula, NotificationType type) {
        events.notify(formula.getId(), authors.formulaRecipient(formula), null, type, formula, formula.getReviewReason());
    }

    public FormulaEntity revise(String id, int expectedVersion, FormulaEntity replacement,
                                String reviewer, String reason) {
        String justification = justification(reason);
        return transactions.write(() -> {
            FormulaEntity current = requireStatus(id, FormulaStatus.PUBLIC);
            if (FormulaVersion.currentNumber(current) != expectedVersion) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "expectedVersion does not match current version");
            }

            formulaService.normalizeFormula(replacement);
            String error = formulaService.prepareFormulaInput(replacement, current);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            requireValidContent(replacement);

            replacement.setId(id);
            requireAvailableName(replacement);
            replacement.setOwnerUserId(current.getOwnerUserId());
            replacement.setOwnerEmail(current.getOwnerEmail());
            replacement.setAuthorId(current.getAuthorId());
            replacement.setCreatedAt(current.getCreatedAt());
            replacement.setUpvotes(current.getUpvotes());
            replacement.setDownvotes(current.getDownvotes());
            replacement.setRatingCount(current.getRatingCount());
            replacement.setAverageRating(current.getAverageRating());
            replacement.setCommentCount(current.getCommentCount());
            replacement.setViewCount(current.getViewCount());
            replacement.setVersion(Math.incrementExact(expectedVersion));
            replacement.setPublicationKey(replacement.getName());
            review(replacement, FormulaStatus.PUBLIC, reviewer, justification);
            archiveLegacy(current);
            writes.replace(current, replacement);
            versions.append(replacement);
            return replacement;
        });
    }

    public void delete(String id) {
        FormulaEntity formula = find(id);
        if (formula.getStatus() != FormulaStatus.PUBLIC) {
            writes.delete(formula);
            return;
        }
        transactions.write(() -> {
            FormulaEntity current = requireStatus(id, FormulaStatus.PUBLIC);
            archiveLegacy(current);
            writes.delete(current);
            return current;
        });
    }

    public List<FormulaVersion> history(String id) {
        var legacy = legacyPublication(id);
        List<FormulaVersion> history = versions.history(id);
        if (!history.isEmpty()) {
            return history;
        }
        return List.of(legacy.orElseThrow(FormulaModerationService::versionNotFound));
    }

    public FormulaVersion version(String id, int number) {
        if (number < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Optional<FormulaVersion> legacy = number == 1 ? legacyPublication(id) : Optional.empty();
        return versions.find(id, number)
                .or(() -> legacy)
                .orElseThrow(FormulaModerationService::versionNotFound);
    }

    private Optional<FormulaVersion> legacyPublication(String id) {
        return formulas.findByIdAndStatus(id, FormulaStatus.PUBLIC)
                .filter(formula -> formula.getVersion() == null)
                .map(FormulaVersion::from);
    }

    private static ResponseStatusException versionNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "formula version not found");
    }

    private void archiveLegacy(FormulaEntity formula) {
        if (formula.getVersion() == null) {
            versions.append(formula);
        }
    }

    private void requireValidContent(FormulaEntity formula) {
        String error = formulaService.validatePublication(formula);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    private void requireAvailableName(FormulaEntity formula) {
        if (formulas.existsByNameAndStatusAndIdNot(formula.getName(), FormulaStatus.PUBLIC, formula.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "public formula name already exists");
        }
    }

    private FormulaEntity requireStatus(String id, FormulaStatus status) {
        FormulaEntity formula = find(id);
        if (formula.getStatus() != status) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "formula must be " + status);
        }
        return formula;
    }

    private FormulaEntity find(String id) {
        return formulas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "formula not found"));
    }

    private void review(FormulaEntity formula, FormulaStatus status, String reviewer, String justification) {
        Instant now = Instant.now();
        formula.setStatus(status);
        formula.setReviewedBy(reviewer);
        formula.setReviewedAt(now);
        formula.setReviewReason(justification);
        formula.setUpdatedAt(now);
    }

    private String justification(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new IllegalArgumentException("reason must contain 1 to 1000 characters");
        }
        return reason.strip();
    }

}

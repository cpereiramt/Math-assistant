package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ModeratedFormula;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.NotificationType;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.Report;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ReportDecision;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ReportReason;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ReportTarget;
import com.claySoftware.MathExpAssistant.entities.FormulaCommentEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import com.claySoftware.MathExpAssistant.repositories.CommunityTransactions;
import com.claySoftware.MathExpAssistant.repositories.FormulaCommentRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaWrites;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;

import static com.claySoftware.MathExpAssistant.models.FormulaStatus.PUBLIC;

@Service
public class CommunityReportService {
    private final FormulaRepository formulas;
    private final FormulaWrites writes;
    private final FormulaCommentRepository comments;
    private final MongoTemplate mongo;
    private final CommunityTransactions transactions;
    private final CommunityEvents events;
    private final PublicAuthorService authors;

    public CommunityReportService(FormulaRepository formulas, FormulaWrites writes, FormulaCommentRepository comments, MongoTemplate mongo,
                                  CommunityTransactions transactions, CommunityEvents events, PublicAuthorService authors) {
        this.formulas = formulas;
        this.writes = writes;
        this.comments = comments;
        this.mongo = mongo;
        this.transactions = transactions;
        this.events = events;
        this.authors = authors;
    }

    public void submit(ReportTarget target, String id, ReportReason reason, String details, String viewer) {
        if (reason == ReportReason.MATH_ERROR && details.isBlank()) {
            throw new IllegalArgumentException("describe the mathematical error");
        }
        transactions.write(() -> {
            FormulaCommentEntity comment = target == ReportTarget.COMMENT ? comment(id) : null;
            String formulaId = comment == null ? id : comment.getFormulaId();
            FormulaEntity formula = formulas.findByIdAndStatus(formulaId, PUBLIC)
                    .orElseThrow(() -> missing("public formula"));
            String owner = comment == null ? authors.formulaRecipient(formula) : authors.recipient(comment.getUserId());
            if (viewer.equals(owner)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot report your own content");
            }
            Integer version = comment == null ? FormulaVersion.currentNumber(formula) : null;
            Instant updated = comment == null ? null : comment.getUpdatedAt();
            String key = CommunityEvents.key(target.name(), id, viewer, Objects.toString(version),
                    Objects.toString(updated), comment == null ? "" : comment.getContent());
            if (mongo.exists(Query.query(Criteria.where("id").is(key)), Report.class)) {
                return null;
            }
            events.limit("report", viewer, 10);
            String evidence = comment == null
                    ? formula.getName() + "\n" + formula.getEquation() + "\n" + Objects.toString(formula.getDescription(), "")
                    : comment.getContent();
            if (evidence.length() > 5000) {
                evidence = evidence.substring(0, 5000);
            }
            mongo.insert(new Report(key, target, id, formulaId, version, updated, viewer,
                    reason, details.strip(), evidence, Instant.now(), null, null, null, null));
            return null;
        });
    }

    public Page<Report> queue(Pageable page) {
        Query query = Query.query(Criteria.where("decision").is(null));
        long count = mongo.count(query, Report.class);
        query.with(page).with(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return new PageImpl<>(mongo.find(query, Report.class), page, count);
    }

    public void resolve(String id, ReportDecision decision, String reason, String reviewer) {
        transactions.write(() -> {
            Report report = mongo.findById(id, Report.class);
            if (report == null) {
                throw missing("report");
            }
            if (report.decision() != null) {
                if (decision != report.decision()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "report already resolved");
                }
                return null;
            }
            if (decision == ReportDecision.REMOVED) {
                removeContent(report, reason.strip());
            }
            mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("decision").is(null)),
                    new Update().set("decision", decision).set("reviewer", reviewer)
                            .set("resolution", reason.strip()).set("resolvedAt", Instant.now()), Report.class);
            return null;
        });
    }

    private void removeContent(Report report, String reason) {
        FormulaEntity formula = formulas.findByIdAndStatus(report.formulaId(), PUBLIC)
                .orElseThrow(CommunityReportService::stale);
        Instant now = Instant.now();
        String owner;
        if (report.target() == ReportTarget.FORMULA) {
            if (FormulaVersion.currentNumber(formula) != report.formulaVersion()) {
                throw stale();
            }
            mongo.insert(new ModeratedFormula(formula.getId(), formula, now));
            writes.delete(formula);
            owner = authors.formulaRecipient(formula);
        } else {
            FormulaCommentEntity comment = comments.findById(report.targetId()).filter(item -> !item.isDeleted())
                    .orElseThrow(CommunityReportService::stale);
            if (!Objects.equals(comment.getUpdatedAt(), report.commentUpdatedAt())
                    || !Objects.equals(comment.getContent(), report.evidence())) {
                throw stale();
            }
            mongo.updateFirst(Query.query(Criteria.where("id").is(comment.getId()).and("deleted").ne(true)),
                    new Update().set("deleted", true).set("updatedAt", now), FormulaCommentEntity.class);
            formulas.updateCommentCount(formula.getId(), comments.countByFormulaIdAndDeletedFalse(formula.getId()), now);
            owner = authors.recipient(comment.getUserId());
        }
        events.notify(report.id(), owner, null, NotificationType.CONTENT_REMOVED, formula, reason);
    }

    private FormulaCommentEntity comment(String id) {
        return comments.findById(id).filter(item -> !item.isDeleted()).orElseThrow(() -> missing("comment"));
    }

    private static ResponseStatusException missing(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found");
    }

    private static ResponseStatusException stale() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "reported content changed or is no longer public");
    }
}

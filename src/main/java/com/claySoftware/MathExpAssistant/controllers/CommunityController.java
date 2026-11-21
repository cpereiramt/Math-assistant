package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.CommunityRecords.Notification;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.Report;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ReportDecision;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ReportReason;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ReportTarget;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.services.CommunityEvents;
import com.claySoftware.MathExpAssistant.services.CommunityReportService;
import com.claySoftware.MathExpAssistant.services.FormulaDiscoveryService;
import com.claySoftware.MathExpAssistant.services.PublicAuthorService;
import com.claySoftware.MathExpAssistant.services.PublicAuthorService.Profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CommunityController {
    private final PublicAuthorService authors;
    private final FormulaDiscoveryService discovery;
    private final CommunityReportService reports;
    private final CommunityEvents events;

    public CommunityController(PublicAuthorService authors, FormulaDiscoveryService discovery,
                               CommunityReportService reports, CommunityEvents events) {
        this.authors = authors;
        this.discovery = discovery;
        this.reports = reports;
        this.events = events;
    }

    @GetMapping("/authors/{id}")
    public Profile author(@PathVariable String id, Authentication auth) {
        return authors.byId(id, viewer(auth));
    }

    @GetMapping("/authors/by-formula/{id}")
    public Profile formulaAuthor(@PathVariable String id, Authentication auth) {
        return authors.byFormula(id, viewer(auth));
    }

    @GetMapping("/authors/me")
    public Profile myProfile(Authentication auth) {
        return authors.mine(viewer(auth));
    }

    @PutMapping("/authors/me")
    public Profile editProfile(@Valid @RequestBody ProfileRequest request, Authentication auth) {
        return authors.edit(viewer(auth), request.name(), request.bio(), request.website());
    }

    @GetMapping("/authors/{id}/formulas")
    public Page<FormulaEntity> publications(@PathVariable String id, @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return authors.publications(id, paging(page, size).withSort(Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @PutMapping("/authors/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@PathVariable String id, Authentication auth) {
        authors.follow(id, viewer(auth), true);
    }

    @DeleteMapping("/authors/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable String id, Authentication auth) {
        authors.follow(id, viewer(auth), false);
    }

    @GetMapping("/formulas/{id}")
    public FormulaEntity detail(@PathVariable String id) {
        return discovery.detail(id);
    }

    @PostMapping("/formulas/{id}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@PathVariable String id, Authentication auth) {
        discovery.recordView(id, viewer(auth));
    }

    @GetMapping("/formulas/ranking")
    public Page<FormulaEntity> ranking(@RequestParam(defaultValue = "30") int days,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return discovery.ranking(days, paging(page, size));
    }

    @PostMapping("/formulas/{id}/reports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reportFormula(@PathVariable String id, @Valid @RequestBody ReportRequest request, Authentication auth) {
        reports.submit(ReportTarget.FORMULA, id, request.reason(), request.details(), viewer(auth));
    }

    @PostMapping("/formulas/comments/{id}/reports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reportComment(@PathVariable String id, @Valid @RequestBody ReportRequest request, Authentication auth) {
        reports.submit(ReportTarget.COMMENT, id, request.reason(), request.details(), viewer(auth));
    }

    @GetMapping("/formulas/admin/reports")
    public Page<Report> reports(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return reports.queue(paging(page, size));
    }

    @PutMapping("/formulas/admin/reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable String id, @Valid @RequestBody ResolutionRequest request, Authentication auth) {
        reports.resolve(id, request.decision(), request.reason(), viewer(auth));
    }

    @GetMapping("/notifications")
    public Page<Notification> inbox(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size, Authentication auth) {
        return events.inbox(viewer(auth), paging(page, size));
    }

    @PutMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable String id, Authentication auth) {
        events.markRead(viewer(auth), id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> communityError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }

    private static String viewer(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    private static PageRequest paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    public record ProfileRequest(@NotBlank @Size(max = 80) String name,
                                 @NotNull @Size(max = 500) String bio,
                                 @NotNull @Size(max = 300) @Pattern(regexp = "(?i)(?:https?://[^\\s/]+[^\\s]*|)") String website) {}
    public record ReportRequest(@NotNull ReportReason reason, @NotNull @Size(max = 1000) String details) {}
    public record ResolutionRequest(@NotNull ReportDecision decision, @NotBlank @Size(max = 1000) String reason) {}
}

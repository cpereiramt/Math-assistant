package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaCommentEntity;
import com.claySoftware.MathExpAssistant.models.FormulaCommentRequest;
import com.claySoftware.MathExpAssistant.models.FormulaCommentUpdateRequest;
import com.claySoftware.MathExpAssistant.models.FormulaCommentResponse;
import com.claySoftware.MathExpAssistant.models.FormulaRatingRequest;
import com.claySoftware.MathExpAssistant.models.ExecuteFormulaRequest;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.services.PlanLimits;
import com.claySoftware.MathExpAssistant.models.FormulaPreviewRequest;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaSearchResponse;
import com.claySoftware.MathExpAssistant.models.FormulaSearchScope;
import com.claySoftware.MathExpAssistant.models.FormulaType;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.CommunityTransactions;
import com.claySoftware.MathExpAssistant.services.CommunityEvents;
import com.claySoftware.MathExpAssistant.services.FormulaService;
import com.claySoftware.MathExpAssistant.services.FormulaSocialService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.validation.Valid;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api/formulas")
public class FormulaController {

    private final FormulaService formulaService;
    private final FormulaSocialService formulaSocialService;
    private final FormulaRepository formulaRepository;
    private final CommunityTransactions transactions;
    private final CommunityEvents events;
    private final PlanLimits planLimits;

    public FormulaController(
            FormulaService formulaService,
            FormulaSocialService formulaSocialService,
            FormulaRepository formulaRepository, CommunityTransactions transactions, CommunityEvents events,
            PlanLimits planLimits) {
        this.formulaRepository = formulaRepository;
        this.transactions = transactions;
        this.events = events;
        this.planLimits = planLimits;
        this.formulaService = formulaService;
        this.formulaSocialService = formulaSocialService;
    }

    @PostMapping("/execute")
    @RateLimiter(name = "publicApi", fallbackMethod = "rateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "bulkheadFallback")
    public String executeFormula(@RequestBody ExecuteFormulaRequest request, Authentication authentication) {
        planLimits.consumeExecution(currentUser(authentication));
        return formulaService.executeFormula(request);
    }

    @PostMapping("/mine/execute")
    public String executeMyFormula(
            @RequestBody ExecuteFormulaRequest request,
            Authentication authentication) {
        String user = currentUser(authentication);
        planLimits.consumeExecution(user);
        return formulaService.executeUserFormula(request, user);
    }

    @PostMapping("/insert")
    @RateLimiter(name = "publicApi", fallbackMethod = "insertRateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "insertBulkheadFallback")
    public String createNewFormula(@RequestBody @Validated FormulaEntity formulaEntity,
                                   Authentication authentication) {
        String result = transactions.write(() -> {
            events.limit("publication", currentUser(authentication), 10);
            return requireValidWrite(formulaService.insertNewFormula(formulaEntity, currentUser(authentication)));
        });
        if (result.equals("Formula already exists")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, result);
        }
        return requireValidWrite(result);
    }

    @GetMapping({ "/getAll", "/public" })
    @RateLimiter(name = "publicApi", fallbackMethod = "getAllRateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "getAllBulkheadFallback")
    public List<FormulaEntity> getAllFormula() {
        return formulaService.listPublicFormulas();
    }

    @GetMapping("/search")
    public FormulaSearchResponse searchFormulas(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<FormulaGroup> groups,
            @RequestParam(required = false) FormulaType type,
            @RequestParam(defaultValue = "PUBLIC") FormulaSearchScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,
            Authentication authentication) {
        return formulaService.searchFormulas(
                q, groups, type, scope, currentUser(authentication), page, size, sortBy, direction);
    }

    @GetMapping("/name/{name}")
    public Optional<FormulaEntity> getFormulaByName(@PathVariable String name) {
        return formulaRepository.findByNameAndStatus(name.toUpperCase(), FormulaStatus.PUBLIC);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getFormulaByStatus(@PathVariable String status) {
        try {
            FormulaStatus formulaStatus = FormulaStatus.valueOf(status.toUpperCase(Locale.ROOT));
            if (formulaStatus != FormulaStatus.PUBLIC) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "only PUBLIC is available"));
            }
            return ResponseEntity.ok(formulaService.listPublicFormulas());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "invalid formula status"));
        }
    }

    @GetMapping("/mine")
    public List<FormulaEntity> getMyFormulas(Authentication authentication) {
        return formulaService.listUserFormulas(currentUser(authentication));
    }

    @GetMapping("/mine/{id}")
    public ResponseEntity<?> getMyFormula(@PathVariable String id, Authentication authentication) {
        return formulaService.getUserFormula(id, currentUser(authentication))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "custom formula not found for user")));
    }

    @PostMapping("/mine")
    public String createMyFormula(
            @RequestBody @Validated FormulaEntity formulaEntity,
            Authentication authentication) {
        String owner = currentUser(authentication);
        return planLimits.createPrivateFormula(owner,
                () -> requireValidWrite(formulaService.insertUserFormula(formulaEntity, owner, owner)));
    }

    @PutMapping("/mine/{id}")
    public String updateMyFormula(
            @PathVariable String id,
            @RequestBody @Validated FormulaEntity formulaEntity,
            Authentication authentication) {
        String owner = currentUser(authentication);
        return requireValidWrite(formulaService.updateUserFormula(id, formulaEntity, owner, owner));
    }

    @DeleteMapping("/mine/{id}")
    public String deleteMyFormula(@PathVariable String id, Authentication authentication) {
        return formulaService.deleteUserFormula(id, currentUser(authentication));
    }

    @PostMapping("/mine/{id}/publish")
    public ResponseEntity<?> publishMyFormula(@PathVariable String id, Authentication authentication) {
        String result = transactions.write(() -> {
            events.limit("publication", currentUser(authentication), 10);
            return formulaService.publishUserFormula(id, currentUser(authentication));
        });
        return ResponseEntity.accepted().body(Map.of("message", result, "status", FormulaStatus.UNDER_REVIEW));
    }

    @PostMapping("/{formulaId}/ratings")
    public FormulaEntity rateFormula(
            @PathVariable String formulaId,
            @Valid @RequestBody FormulaRatingRequest request,
            Authentication authentication) {
        if (request == null || request.value() == null) {
            throw new IllegalArgumentException("rating value is required");
        }
        return transactions.write(() -> formulaSocialService.rate(formulaId, currentUser(authentication), request.value()));
    }

    @DeleteMapping("/{formulaId}/ratings")
    public FormulaEntity removeFormulaRating(
            @PathVariable String formulaId,
            Authentication authentication) {
        return transactions.write(() -> formulaSocialService.removeRating(formulaId, currentUser(authentication)));
    }

    @GetMapping("/{formulaId}/comments")
    public Page<FormulaCommentResponse> listFormulaComments(
            @PathVariable String formulaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        return formulaSocialService.listComments(
                formulaId, currentUser(authentication), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{formulaId}/comments/{commentId}/replies")
    public Page<FormulaCommentResponse> listCommentReplies(
            @PathVariable String formulaId,
            @PathVariable String commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        return formulaSocialService.listReplies(
                formulaId, commentId, currentUser(authentication), PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
    }

    @PostMapping("/{formulaId}/comments")
    public FormulaCommentEntity addFormulaComment(
            @PathVariable String formulaId,
            @Valid @RequestBody FormulaCommentRequest request,
            Authentication authentication) {
        return transactions.write(() -> formulaSocialService.addComment(formulaId, currentUser(authentication), request));
    }

    @PatchMapping("/comments/{commentId}")
    public FormulaCommentEntity updateFormulaComment(
            @PathVariable String commentId,
            @Valid @RequestBody FormulaCommentUpdateRequest request,
            Authentication authentication) {
        return transactions.write(() -> formulaSocialService.updateComment(commentId, currentUser(authentication), request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteFormulaComment(
            @PathVariable String commentId,
            Authentication authentication) {
        transactions.write(() -> {
            formulaSocialService.deleteComment(commentId, currentUser(authentication));
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mine/validate")
    public Map<String, Object> validateMyFormula(@RequestBody @Validated FormulaEntity formulaEntity,
                                                @RequestParam(required = false) String formulaId,
                                                Authentication authentication) {
        String validationError = formulaService.validateUserFormula(formulaEntity, editContext(formulaId, authentication));
        if (validationError == null) {
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("valid", true);
            response.put("message", "formula is valid");
            response.put("inputMode", formulaEntity.getInputMode());
            response.put("equation", formulaEntity.getEquation());
            response.put("displayEquation", formulaEntity.getDisplayEquation());
            response.put("parameters", formulaEntity.getParameters());
            if (formulaEntity.getSourceSnapshots() != null) {
                response.put("sourceSnapshots", formulaEntity.getSourceSnapshots());
            }
            return response;
        }
        return Map.of("valid", false, "message", validationError);
    }

    @GetMapping("/builder/catalog")
    public List<FormulaEntity> getBuilderCatalog() {
        return formulaService.listBuilderCatalog();
    }

    @PostMapping("/builder/preview")
    public Map<String, Object> previewBuilderFormula(@RequestBody FormulaPreviewRequest request,
                                                     @RequestParam(required = false) String formulaId,
                                                     Authentication authentication) {
        FormulaEntity context = editContext(formulaId, authentication);
        planLimits.consumeBuilderPreview(currentUser(authentication));
        return formulaService.previewUserFormula(request, context);
    }

    private FormulaEntity editContext(String id, Authentication authentication) {
        if (id == null) {
            return null;
        }
        return formulaService.getUserFormula(id, currentUser(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "custom formula not found for user"));
    }

    private String requireValidWrite(String result) {
        if (result.startsWith("validation_error:")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result);
        }
        return result;
    }

    private String currentUser(Authentication authentication) {
        return authentication == null ? "" : authentication.getName();
    }

    private String rateLimitFallback(ExecuteFormulaRequest request, Authentication authentication, RequestNotPermitted ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", ex);
    }

    private String bulkheadFallback(ExecuteFormulaRequest request, Authentication authentication, BulkheadFullException ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Server busy", ex);
    }

    private List<FormulaEntity> getAllRateLimitFallback(RequestNotPermitted ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", ex);
    }

    private List<FormulaEntity> getAllBulkheadFallback(BulkheadFullException ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Server busy", ex);
    }

    private String insertRateLimitFallback(FormulaEntity formulaEntity, Authentication authentication, RequestNotPermitted ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", ex);
    }

    private String insertBulkheadFallback(FormulaEntity formulaEntity, Authentication authentication, BulkheadFullException ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Server busy", ex);
    }
}

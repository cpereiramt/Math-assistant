package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.models.ExecuteFormulaRequest;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaPreviewRequest;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.services.FormulaService;
import com.claySoftware.MathExpAssistant.utils.AdminBypass;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api/formulas")
public class FormulaController {

    private final FormulaService formulaService;
    private final FormulaRepository formulaRepository;
    private final AdminBypass adminBypass;

    public FormulaController(
            FormulaService formulaService,
            FormulaRepository formulaRepository,
            AdminBypass adminBypass) {
        this.formulaRepository = formulaRepository;
        this.formulaService = formulaService;
        this.adminBypass = adminBypass;
    }

    @PostMapping("/execute")
    @RateLimiter(name = "publicApi", fallbackMethod = "rateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "bulkheadFallback")
    public String executeFormula(@RequestBody ExecuteFormulaRequest request) {
        return formulaService.executeFormula(request);
    }

    @PostMapping("/mine/execute")
    public String executeMyFormula(
            @RequestBody ExecuteFormulaRequest request,
            Authentication authentication) {
        return formulaService.executeUserFormula(request, currentUser(authentication));
    }

    @PostMapping("/insert")
    @RateLimiter(name = "publicApi", fallbackMethod = "insertRateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "insertBulkheadFallback")
    public String createNewFormula(@RequestBody @Validated FormulaEntity formulaEntity) {
        formulaEntity.setStatus(FormulaStatus.UNDER_REVIEW);
        return formulaService.insertNewFormula(formulaEntity);
    }

    @GetMapping({ "/getAll", "/public" })
    @RateLimiter(name = "publicApi", fallbackMethod = "getAllRateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "getAllBulkheadFallback")
    public List<FormulaEntity> getAllFormula() {
        return formulaService.listPublicFormulas();
    }

    @GetMapping("/name/{name}")
    public Optional<FormulaEntity> getFormulaByName(@PathVariable String name) {
        return formulaRepository.findByNameAndStatus(name.toUpperCase(), FormulaStatus.PUBLIC);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getFormulaByStatus(@PathVariable String status) {
        try {
            FormulaStatus formulaStatus = FormulaStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(formulaRepository.findAllByStatus(formulaStatus));
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
        return formulaService.insertUserFormula(formulaEntity, owner, owner);
    }

    @PutMapping("/mine/{id}")
    public String updateMyFormula(
            @PathVariable String id,
            @RequestBody @Validated FormulaEntity formulaEntity,
            Authentication authentication) {
        String owner = currentUser(authentication);
        return formulaService.updateUserFormula(id, formulaEntity, owner, owner);
    }

    @DeleteMapping("/mine/{id}")
    public String deleteMyFormula(@PathVariable String id, Authentication authentication) {
        return formulaService.deleteUserFormula(id, currentUser(authentication));
    }

    @PostMapping("/mine/validate")
    public Map<String, Object> validateMyFormula(@RequestBody @Validated FormulaEntity formulaEntity) {
        String validationError = formulaService.validateUserFormula(formulaEntity);
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
    public Map<String, Object> previewBuilderFormula(@RequestBody FormulaPreviewRequest request) {
        return formulaService.previewUserFormula(request);
    }

    @DeleteMapping("/delete/{id}")
    public String deleteFormula(@PathVariable String id, Authentication authentication) {
        Optional<FormulaEntity> formulaToDelete = formulaRepository.findById(id);
        String email = currentUser(authentication);

        if (!adminBypass.isAdminEmail(email)) {
            return "Access denied";
        }

        if (formulaToDelete.isPresent()) {
            formulaRepository.delete(formulaToDelete.get());
            return "formula deleted with success !";
        }
        return "formula not found !";
    }

    private String currentUser(Authentication authentication) {
        return authentication == null ? "" : authentication.getName();
    }

    private String rateLimitFallback(ExecuteFormulaRequest request, RequestNotPermitted ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", ex);
    }

    private String bulkheadFallback(ExecuteFormulaRequest request, BulkheadFullException ex) {
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

    private String insertRateLimitFallback(FormulaEntity formulaEntity, RequestNotPermitted ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", ex);
    }

    private String insertBulkheadFallback(FormulaEntity formulaEntity, BulkheadFullException ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Server busy", ex);
    }
}

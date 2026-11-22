package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.DecimalCalculation;
import com.claySoftware.MathExpAssistant.models.CalculationRequest;
import com.claySoftware.MathExpAssistant.models.CalculationResult;
import com.claySoftware.MathExpAssistant.services.FormulaService;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/formulas")
public class CalculationController {
    private final FormulaService formulas;

    public CalculationController(FormulaService formulas) {
        this.formulas = formulas;
    }

    @GetMapping("/calculation/functions")
    public List<String> functions() {
        return DecimalCalculation.FUNCTIONS;
    }

    @PostMapping("/calculate")
    @RateLimiter(name = "publicApi")
    @Bulkhead(name = "publicApi")
    public CalculationResult calculate(@RequestBody CalculationRequest request) {
        return formulas.calculate(request, null);
    }

    @PostMapping("/mine/calculate")
    @RateLimiter(name = "publicApi")
    @Bulkhead(name = "publicApi")
    public CalculationResult calculateMine(@RequestBody CalculationRequest request, Authentication authentication) {
        return formulas.calculate(request, authentication.getName());
    }

    @PostMapping("/builder/calculate")
    @RateLimiter(name = "publicApi")
    @Bulkhead(name = "publicApi")
    public CalculationResult preview(@RequestBody Preview preview, @RequestParam(required = false) String formulaId,
                                     Authentication authentication) {
        return formulas.calculatePreview(preview.formula(), preview.inputs(), formulaId, authentication.getName(), false);
    }

    @PostMapping("/admin/{id}/calculate-preview")
    @RateLimiter(name = "publicApi")
    @Bulkhead(name = "publicApi")
    public CalculationResult previewRevision(@PathVariable String id, @RequestBody Preview preview) {
        return formulas.calculatePreview(preview.formula(), preview.inputs(), id, null, true);
    }

    @PostMapping("/admin/{id}/validate-revision")
    public Map<String, Object> validateRevision(@PathVariable String id, @RequestBody FormulaEntity formula) {
        String error = formulas.validatePublicRevision(formula, id);
        if (error != null) return Map.of("valid", false, "message", error);
        return Map.of("valid", true, "message", "formula is valid", "equation", formula.getEquation(),
                "displayEquation", formula.getDisplayEquation(),
                "parameters", formula.getParameters() == null ? List.of() : formula.getParameters());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return error(400, "validation_error", error.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidJson() {
        return error(400, "invalid_json", "Expected a JSON request with finite decimal values");
    }

    @ExceptionHandler(DecimalCalculation.Failure.class)
    public ResponseEntity<Map<String, String>> failed(DecimalCalculation.Failure failure) {
        return error(422, failure.getCode(), failure.getMessage());
    }

    @ExceptionHandler({RequestNotPermitted.class, BulkheadFullException.class})
    public ResponseEntity<Map<String, String>> busy() {
        return error(429, "calculation_busy", "Too many calculations. Try again shortly");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException failure) {
        return error(failure.getStatusCode().value(), "not_found", failure.getReason());
    }

    private ResponseEntity<Map<String, String>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }

    public record Preview(FormulaEntity formula, CalculationRequest inputs) {}
}

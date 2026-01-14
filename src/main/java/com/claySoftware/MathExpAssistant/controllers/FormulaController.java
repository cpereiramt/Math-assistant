package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.services.FormulaService;

import org.springframework.aop.support.AopUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.script.ScriptException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

@RestController
@RequestMapping("/api/formulas")
public class FormulaController {

    private final FormulaService formulaService;
    private final FormulaRepository formulaRepository;

    public FormulaController(FormulaService formulaService, FormulaRepository formulaRepository) {
        this.formulaRepository = formulaRepository;
        this.formulaService = formulaService;
    }

    @PostMapping("/execute")
    @RateLimiter(name = "publicApi", fallbackMethod = "rateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "bulkheadFallback")
    public String executeFormula(
            @RequestParam String formulaName,
            @RequestBody Map<String, Double> variables) throws ScriptException {
        // TODO : Implement the logic to use some ai mathematics model when the formula
        // is not found on database
        return formulaService.executeFormula(formulaName, variables);
    }

    @PostMapping("/insert")
    @RateLimiter(name = "publicApi", fallbackMethod = "insertRateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "insertBulkheadFallback")
    public String createNewFormula(@RequestBody @Validated FormulaEntity formulaEntity) {
        formulaEntity.setStatus("private");
        return formulaService.insertNewFormula(formulaEntity);
    }

    @DeleteMapping("/{id}")
    public String deleteFormula(@PathVariable String id) {
        Optional<FormulaEntity> formulaToDelete = formulaRepository.findById(id);
        if (formulaToDelete.isPresent()) {
            formulaRepository.delete(formulaToDelete.get());
            return "formula deleted with success !";
        }
        return "formula not found !";

    }

    @GetMapping("/getAll")
    @RateLimiter(name = "publicApi", fallbackMethod = "getAllRateLimitFallback")
    @Bulkhead(name = "publicApi", fallbackMethod = "getAllBulkheadFallback")
    public List<FormulaEntity> getAllFormula() {
        Optional<List<FormulaEntity>> formulaList = formulaRepository.findAllByStatus("public");
        return formulaList.get();
    }

    @GetMapping("/{name}")
    public Optional<FormulaEntity> getFormulaByName(@PathVariable String name) {
        return formulaRepository.findByName(name);
    }

    private String rateLimitFallback(String formulaName,
            Map<String, Double> variables,
            RequestNotPermitted ex) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", ex);
    }

    private String bulkheadFallback(String formulaName,
            Map<String, Double> variables,
            BulkheadFullException ex) {
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

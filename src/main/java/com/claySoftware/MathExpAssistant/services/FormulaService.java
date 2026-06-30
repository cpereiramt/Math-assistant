package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.EquationBuilder;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.helpers.FormulaValidator;
import com.claySoftware.MathExpAssistant.helpers.VariableBuilder;
import com.claySoftware.MathExpAssistant.models.ExecuteFormulaRequest;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.utils.FormulaNormalizer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FormulaService {

    private final FormulaRepository formulaRepository;
    private final FormulaExecutor formulaExecutor;
    private final FormulaValidator validator;

    public FormulaService(
            FormulaExecutor formulaExecutor,
            FormulaRepository formulaRepository,
            FormulaValidator validator) {
        this.formulaExecutor = formulaExecutor;
        this.formulaRepository = formulaRepository;
        this.validator = validator;
    }

    public List<FormulaEntity> listPublicFormulas() {
        return formulaRepository.findAllByStatus(FormulaStatus.PUBLIC);
    }

    public List<FormulaEntity> listUserFormulas(String ownerUserId) {
        return formulaRepository.findAllByOwnerUserIdAndStatus(ownerUserId, FormulaStatus.PRIVATE);
    }

    public Optional<FormulaEntity> getUserFormula(String id, String ownerUserId) {
        return formulaRepository.findByIdAndOwnerUserIdAndStatus(id, ownerUserId, FormulaStatus.PRIVATE);
    }

    // TODO : Put some form of cache, maybe using caffeine
    public String executeFormula(ExecuteFormulaRequest req) {
        if (req == null) {
            return "validation_error: request body is required";
        }
        if (req.getFormulaName() == null || req.getFormulaName().isBlank()) {
            return "validation_error: formulaName is required";
        }

        Optional<FormulaEntity> opt = formulaRepository.findByNameAndStatus(
                FormulaNormalizer.normalizeEquation(req.getFormulaName()),
                FormulaStatus.PUBLIC);

        if (opt.isEmpty()) {
            return "not_found: formula not found on database";
        }

        return executeExistingFormula(opt.get(), req);
    }

    public String executeUserFormula(ExecuteFormulaRequest req, String ownerUserId) {
        if (req == null) {
            return "validation_error: request body is required";
        }
        if (req.getFormulaName() == null || req.getFormulaName().isBlank()) {
            return "validation_error: formulaName is required";
        }

        Optional<FormulaEntity> opt = formulaRepository.findByNameAndOwnerUserIdAndStatus(
                FormulaNormalizer.normalizeEquation(req.getFormulaName()),
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (opt.isEmpty()) {
            return "not_found: custom formula not found for user";
        }

        return executeExistingFormula(opt.get(), req);
    }

    public String insertNewFormula(FormulaEntity formulaEntity) {
        normalizeFormula(formulaEntity);

        String validationError = validator.validateFormulaForInsert(formulaEntity);
        if (validationError != null) {
            return "validation_error: " + validationError;
        }

        FormulaEntity existingFormula = formulaRepository
                .findByNameAndStatus(formulaEntity.getName(), FormulaStatus.PUBLIC)
                .orElse(null);

        if (existingFormula != null && existingFormula.getId() != null) {
            return "Formula already exists";
        }

        Instant now = Instant.now();
        formulaEntity.setCreatedAt(now);
        formulaEntity.setUpdatedAt(now);
        formulaRepository.save(formulaEntity);
        return "new formula successful saved";
    }

    public String validateUserFormula(FormulaEntity formulaEntity) {
        normalizeFormula(formulaEntity);
        formulaEntity.setVariable(false);
        formulaEntity.setStatus(FormulaStatus.PRIVATE);
        return validateCustomFormula(formulaEntity);
    }

    public String insertUserFormula(FormulaEntity formulaEntity, String ownerUserId, String ownerEmail) {
        normalizeFormula(formulaEntity);
        prepareCustomFormulaForOwner(formulaEntity, ownerUserId, ownerEmail);

        String validationError = validateCustomFormula(formulaEntity);
        if (validationError != null) {
            return "validation_error: " + validationError;
        }

        Optional<FormulaEntity> existingFormula = formulaRepository.findByNameAndGroupAndOwnerUserIdAndStatus(
                formulaEntity.getName(),
                formulaEntity.getGroup(),
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (existingFormula.isPresent()) {
            return "validation_error: custom formula already exists for this name and group";
        }

        Instant now = Instant.now();
        formulaEntity.setCreatedAt(now);
        formulaEntity.setUpdatedAt(now);
        formulaRepository.save(formulaEntity);
        return "new custom formula successful saved";
    }

    public String updateUserFormula(String id, FormulaEntity formulaEntity, String ownerUserId, String ownerEmail) {
        Optional<FormulaEntity> existing = formulaRepository.findByIdAndOwnerUserIdAndStatus(
                id,
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (existing.isEmpty()) {
            return "not_found: custom formula not found for user";
        }

        normalizeFormula(formulaEntity);
        prepareCustomFormulaForOwner(formulaEntity, ownerUserId, ownerEmail);
        formulaEntity.setId(id);
        formulaEntity.setCreatedAt(existing.get().getCreatedAt());
        formulaEntity.setUpdatedAt(Instant.now());

        String validationError = validateCustomFormula(formulaEntity);
        if (validationError != null) {
            return "validation_error: " + validationError;
        }

        Optional<FormulaEntity> duplicate = formulaRepository.findByNameAndGroupAndOwnerUserIdAndStatus(
                formulaEntity.getName(),
                formulaEntity.getGroup(),
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (duplicate.isPresent() && !id.equals(duplicate.get().getId())) {
            return "validation_error: custom formula already exists for this name and group";
        }

        formulaRepository.save(formulaEntity);
        return "custom formula successful updated";
    }

    public String deleteUserFormula(String id, String ownerUserId) {
        Optional<FormulaEntity> existing = formulaRepository.findByIdAndOwnerUserIdAndStatus(
                id,
                ownerUserId,
                FormulaStatus.PRIVATE);

        if (existing.isEmpty()) {
            return "not_found: custom formula not found for user";
        }

        formulaRepository.delete(existing.get());
        return "custom formula deleted with success";
    }

    private String executeExistingFormula(FormulaEntity formula, ExecuteFormulaRequest req) {
        boolean isVariadic = Boolean.TRUE.equals(formula.isVariable());

        if (!isVariadic) {
            return executeFixed(formula, req.getVariables());
        }

        return executeVariadic(formula, req.getValues());
    }

    private String executeFixed(FormulaEntity formula, Map<String, Double> variables) {
        if (variables == null || variables.isEmpty()) {
            return "validation_error: variables is required for fixed formulas";
        }

        if (formula.getParameters() == null || formula.getParameters().isEmpty()) {
            return "server_error: fixed formula missing parameters configuration";
        }

        if (formula.getEquation() == null || formula.getEquation().isBlank()) {
            return "server_error: fixed formula missing equation configuration";
        }

        if (!variables.keySet().containsAll(formula.getParameters())) {
            return "validation_error: some parameters are not provided. Expected: " + formula.getParameters();
        }

        if (!formula.getParameters().containsAll(variables.keySet())) {
            return "validation_error: extra parameters provided. Expected only: " + formula.getParameters();
        }

        try {
            Map<String, Double> normalizedVars = FormulaNormalizer.normalizeVariables(variables);
            double result = formulaExecutor.executeFormula(formula.getEquation(), normalizedVars);
            return String.valueOf(result);
        } catch (Exception e) {
            return "calculation_error: " + e.getMessage();
        }
    }

    private String executeVariadic(FormulaEntity formula, List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "validation_error: values is required for variadic formulas";
        }

        if (formula.getEquation() == null || formula.getEquation().isBlank()) {
            return "server_error: variadic formula missing equation configuration";
        }

        Integer min = formula.getMinParameters();
        Integer max = formula.getMaxParameters();

        if (min != null && values.size() < min) {
            return "validation_error: at least " + min + " values are required";
        }
        if (max != null && values.size() > max) {
            return "validation_error: at most " + max + " values are allowed";
        }

        String equation;
        Map<String, Double> vars;

        try {
            equation = EquationBuilder.buildVariadicEquation(formula.getEquation(), values.size());
            vars = VariableBuilder.buildVariables(values);
        } catch (Exception e) {
            return "validation_error: " + e.getMessage();
        }

        try {
            double result = formulaExecutor.executeFormula(equation, vars);
            return String.valueOf(result);
        } catch (Exception e) {
            return "calculation_error: " + e.getMessage();
        }
    }

    private void normalizeFormula(FormulaEntity formula) {
        if (formula == null) {
            return;
        }

        if (formula.getName() != null) {
            formula.setName(formula.getName().trim().toUpperCase());
        }

        if (formula.getEquation() != null) {
            formula.setEquation(FormulaNormalizer.normalizeEquation(formula.getEquation()));
        }

        if (formula.getDisplayEquation() == null || formula.getDisplayEquation().isBlank()) {
            formula.setDisplayEquation(formula.getEquation());
        } else {
            formula.setDisplayEquation(formula.getDisplayEquation().trim());
        }

        if (formula.getParameters() != null) {
            formula.setParameters(FormulaNormalizer.normalizeParameters(formula.getParameters()));
        }
    }

    private void prepareCustomFormulaForOwner(FormulaEntity formula, String ownerUserId, String ownerEmail) {
        formula.setStatus(FormulaStatus.PRIVATE);
        formula.setOwnerUserId(ownerUserId);
        formula.setOwnerEmail(ownerEmail);
        formula.setVariable(false);
        formula.setMinParameters(null);
        formula.setMaxParameters(null);
    }

    private String validateCustomFormula(FormulaEntity formula) {
        if (formula == null) {
            return "Payload is required";
        }

        if (formula.isVariable()) {
            return "custom formulas currently support fixed equations only";
        }

        String validationError = validator.validateFormulaForInsert(formula);
        if (validationError != null) {
            return validationError;
        }

        try {
            Map<String, Double> testVariables = new HashMap<>();
            for (String parameter : formula.getParameters()) {
                testVariables.put(parameter, 1.0);
            }
            formulaExecutor.executeFormula(formula.getEquation(), testVariables);
            return null;
        } catch (Exception e) {
            return "invalid equation syntax: " + e.getMessage();
        }
    }
}

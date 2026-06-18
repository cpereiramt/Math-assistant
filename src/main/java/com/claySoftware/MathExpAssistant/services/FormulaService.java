package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.EquationBuilder;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.models.ExecuteFormulaRequest;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.springframework.stereotype.Service;
import javax.script.ScriptException;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import com.claySoftware.MathExpAssistant.helpers.FormulaValidator;
import com.claySoftware.MathExpAssistant.helpers.VariableBuilder;
import com.claySoftware.MathExpAssistant.utils.FormulaNormalizer;

@Service
public class FormulaService {

    private final FormulaRepository formulaRepository;

    private final FormulaExecutor formulaExecutor;

    private final FormulaValidator validator;

    public FormulaService(FormulaExecutor formulaExecutor, FormulaRepository formulaRepository,
            FormulaValidator validator) {
        this.formulaExecutor = formulaExecutor;
        this.formulaRepository = formulaRepository;
        this.validator = validator;
    }

    // TODO : Put some form of cache, maybe using caffeine
    public String executeFormula(ExecuteFormulaRequest req) throws ScriptException {
        if (req == null)
            return "validation_error: request body is required";
        if (req.getFormulaName() == null || req.getFormulaName().isBlank()) {
            return "validation_error: formulaName is required";
        }

        // 2) buscar fórmula
        Optional<FormulaEntity> opt = formulaRepository.findByName(req.getFormulaName());
        if (opt.isEmpty())
            return "not_found: formula not found on database";

        FormulaEntity formula = opt.get();
        boolean isVariadic = Boolean.TRUE.equals(formula.isVariable());

        // 3) executar conforme tipo
        if (!isVariadic) {
            return executeFixed(formula, req.getVariables());
        } else {
            return executeVariadic(formula, req.getValues());
        }
    }

    public String insertNewFormula(FormulaEntity formulaEntity) {
        String validationError = validator.validateFormulaForInsert(formulaEntity);
        if (validationError != null) {
            return "validation_error: " + validationError;
        }
        FormulaEntity existingFormula = formulaRepository.findByName(formulaEntity.getName()).orElse(null);

        if (existingFormula != null && existingFormula.getId() != null) {
            return "Formula already exists";
        }
        FormulaEntity newFormula = formulaRepository.save(formulaEntity);
        return "new formula successful saved";
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

        // Deve conter todos os parâmetros esperados
        if (!variables.keySet().containsAll(formula.getParameters())) {
            return "validation_error: some parameters are not provided. Expected: " + formula.getParameters();
        }

        // (Opcional) bloquear parâmetros extras para evitar “lixo” no payload
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

        // (Opcional) validar min/max se existirem
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
}

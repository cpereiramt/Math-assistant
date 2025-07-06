package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

@Service
public class FormulaService {

    @Autowired
    private FormulaRepository formulaRepository;

    private final FormulaExecutor formulaExecutor = new FormulaExecutor();
    //TODO : Put some form of cache, maybe using caffeine
    public double executeFormula(String formulaName, Map<String, Double> variables) throws ScriptException {
        // Busca a fórmula pelo nome
        FormulaEntity formulaEntity = formulaRepository.findByName(formulaName)
                .orElseThrow(() -> new IllegalArgumentException("Fórmula não encontrada"));

        // Verifica se todos os parâmetros esperados foram fornecidos
        if (!variables.keySet().containsAll(formulaEntity.getParameters())) {
            throw new IllegalArgumentException("Parâmetros insuficientes ou incorretos fornecidos");
        }

        // Executa a fórmula com as variáveis fornecidas
        return formulaExecutor.executeFormula(formulaEntity.getEquation(), variables);
    }
}


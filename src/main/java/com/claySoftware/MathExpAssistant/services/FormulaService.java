package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class FormulaService {

    @Autowired
    private FormulaRepository formulaRepository;

    private final FormulaExecutor formulaExecutor = new FormulaExecutor();
    //TODO : Put some form of cache, maybe using caffeine
    public String executeFormula(String formulaName, Map<String, Double> variables) throws ScriptException {
        Optional<FormulaEntity> formulaEntity = formulaRepository.findByName(formulaName);
        if(formulaEntity.isEmpty()) {
            return "Formula not Found on database";
        }
        if (!variables.keySet().containsAll(formulaEntity.get().getParameters())) {
            return "some parameters are not provide for calculation";
        }
        return String.valueOf(formulaExecutor.executeFormula(formulaEntity.get().getEquation(), variables));
    }

    public String insertNewFormula(FormulaEntity formulaEntity) {
        
        FormulaEntity existingFormula = formulaRepository.findByName(formulaEntity.getName()).orElse(null);
        
        if (existingFormula != null && existingFormula.getId() != null) {
            return "Formula already exists";
        }
        FormulaEntity newFormula = formulaRepository.save(formulaEntity);
        return "new formula successful saved";
    }
}


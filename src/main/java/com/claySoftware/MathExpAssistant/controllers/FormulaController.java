package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.services.FormulaService;
import org.springframework.web.bind.annotation.*;

import javax.script.ScriptException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public double executeFormula(
            @RequestParam String formulaName,
            @RequestBody Map<String, Double> variables) throws ScriptException {

        return formulaService.executeFormula(formulaName, variables);
    }
    //TODO : posteriormente refatorar os endpoints abaixo para colocar a chamadas e tratamentos no FormulaService
    @PostMapping("/insert")
    public String createNewFormula(@RequestBody  FormulaEntity formulaEntity) {


        FormulaEntity isFormulaDifferentOfNull =  formulaRepository.save(formulaEntity);
        if(isFormulaDifferentOfNull != null) {
            return "new formula successful saved";
        }
        return "Error when trying to save";
    }

    @GetMapping("/getAll")
    public List<FormulaEntity> getAllFormula() {
        return formulaRepository.findAll();
    }

    @GetMapping("/{name}")
    public Optional<FormulaEntity> getFormulaByName(@PathVariable String name) {
        return formulaRepository.findByName(name);
   }
}

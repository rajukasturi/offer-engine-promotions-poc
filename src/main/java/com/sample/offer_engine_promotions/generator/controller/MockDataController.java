package com.sample.offer_engine_promotions.generator.controller;

import com.sample.offer_engine_promotions.generator.model.GenerationResult;
import com.sample.offer_engine_promotions.generator.service.DataGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mock-data")
public class MockDataController {

    private final DataGenerationService dataGenerationService;

    public MockDataController(DataGenerationService dataGenerationService) {
        this.dataGenerationService = dataGenerationService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateData(
            @RequestParam(defaultValue = "100000") int customerCount) {

        try {
            long startTime = System.currentTimeMillis();

            GenerationResult result = dataGenerationService.generateSampleData(customerCount);

            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(Map.of(
                    "message", "Successfully generated mock data.",
                    "generationTimeMs", duration,
                    "customerCount", customerCount,
                    "files", result
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to generate files: " + e.getMessage());
        }
    }
}

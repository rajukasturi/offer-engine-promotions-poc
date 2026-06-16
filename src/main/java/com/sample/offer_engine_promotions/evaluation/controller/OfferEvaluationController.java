package com.sample.offer_engine_promotions.evaluation.controller;

import com.sample.offer_engine_promotions.evaluation.model.OfferEvaluationResponse;
import com.sample.offer_engine_promotions.evaluation.service.OfferEvaluationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluations")
public class OfferEvaluationController {

    private final OfferEvaluationService evaluationService;

    public OfferEvaluationController(OfferEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    // =========================================================
    // ENDPOINT 1: FETCH FROM POSTGRESQL DB
    // =========================================================
    @GetMapping("/db/customers/{customerId}/merchants/{merchantId}")
    public ResponseEntity<List<OfferEvaluationResponse>> evaluateViaDb(
            @PathVariable Integer customerId,
            @PathVariable Integer merchantId) {

        List<OfferEvaluationResponse> offers = evaluationService.getOffersFromDatabase(customerId, merchantId);

        if (offers.isEmpty()) {
            return ResponseEntity.noContent().build(); // Standard 204 No Content if no offers exist
        }
        return ResponseEntity.ok(offers);
    }

    // =========================================================
    // ENDPOINT 2: FETCH FROM REDIS CACHE
    // =========================================================
    @GetMapping(value = "/redis/customers/{customerId}/merchants/{merchantId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> evaluateViaRedis(
            @PathVariable Integer customerId,
            @PathVariable Integer merchantId) {

        String jsonPayload = evaluationService.getOffersFromRedis(customerId, merchantId);

        if (jsonPayload == null || jsonPayload.isBlank()) {
            return ResponseEntity.noContent().build();
        }

        // We pass the raw JSON string straight to the HTTP response body
        return ResponseEntity.ok(jsonPayload);
    }
}
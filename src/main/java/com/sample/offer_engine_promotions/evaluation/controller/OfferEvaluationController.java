package com.sample.offer_engine_promotions.evaluation.controller;

import com.sample.offer_engine_promotions.evaluation.model.OfferEvaluationResponse;
import com.sample.offer_engine_promotions.evaluation.service.OfferEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/evaluations")
public class OfferEvaluationController {

    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluationController.class);
    private static final int CUSTOMER_ID_MAX = 999999999;
    private static final int MERCHANT_ID_MAX = 999999999;
    
    private final OfferEvaluationService evaluationService;

    public OfferEvaluationController(OfferEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    // =========================================================
    // ENDPOINT 1: FETCH FROM POSTGRESQL DB
    // Issue #3: Added cache headers for CDN caching
    // Issue #4: Added input validation with @Min/@Max
    // =========================================================
    @GetMapping("/db/customers/{customerId}/merchants/{merchantId}")
    public ResponseEntity<List<OfferEvaluationResponse>> evaluateViaDb(
            @PathVariable @Min(1) @Max(CUSTOMER_ID_MAX) Integer customerId,
            @PathVariable @Min(1) @Max(MERCHANT_ID_MAX) Integer merchantId) {

        logger.debug("Evaluating offers via database for customer {} merchant {}", customerId, merchantId);
        
        List<OfferEvaluationResponse> offers = evaluationService.getOffersFromDatabase(customerId, merchantId);

        if (offers.isEmpty()) {
            logger.debug("No offers found for customer {} merchant {}", customerId, merchantId);
            return ResponseEntity.noContent().build();
        }
        
        logger.debug("Found {} offers for customer {} merchant {}", offers.size(), customerId, merchantId);
        
        // Issue #3: Add cache headers - offers don't change frequently, cache for 1 hour
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
            .body(offers);
    }

    // =========================================================
    // ENDPOINT 2: FETCH FROM REDIS CACHE WITH FALLBACK
    // Issue #2: Redis fallback implemented in service layer
    // Issue #3: Added cache headers for CDN caching
    // Issue #4: Added input validation with @Min/@Max
    // =========================================================
    @GetMapping(value = "/redis/customers/{customerId}/merchants/{merchantId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> evaluateViaRedis(
            @PathVariable @Min(1) @Max(CUSTOMER_ID_MAX) Integer customerId,
            @PathVariable @Min(1) @Max(MERCHANT_ID_MAX) Integer merchantId) {

        logger.debug("Evaluating offers via redis (with fallback) for customer {} merchant {}", customerId, merchantId);
        
        String jsonPayload = evaluationService.getOffersFromRedis(customerId, merchantId);

        if (jsonPayload == null || jsonPayload.isBlank()) {
            logger.debug("No offers found for customer {} merchant {} (redis/db)", customerId, merchantId);
            return ResponseEntity.noContent().build();
        }

        logger.debug("Found offers for customer {} merchant {} (redis/db)", customerId, merchantId);
        
        // Issue #3: Add cache headers - offers don't change frequently, cache for 1 hour
        // We pass the raw JSON string straight to the HTTP response body
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
            .body(jsonPayload);
    }
}
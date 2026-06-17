package com.sample.offer_engine_promotions.evaluation.service;

import com.sample.offer_engine_promotions.evaluation.model.OfferEvaluationResponse;
import com.sample.offer_engine_promotions.evaluation.repository.CustomerOfferQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OfferEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(OfferEvaluationService.class);
    private final CustomerOfferQueryRepository dbRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public OfferEvaluationService(CustomerOfferQueryRepository dbRepository, 
                                  StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper) {
        this.dbRepository = dbRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Database Fetch: Executed via pure Spring JDBC Client.
     * Issue #8: Added performance monitoring with metrics
     */
    @Transactional(readOnly = true)
    public List<OfferEvaluationResponse> getOffersFromDatabase(Integer customerId, Integer merchantId) {
        long startTime = System.nanoTime();
        try {
            List<OfferEvaluationResponse> results = dbRepository.findActiveOffersForCustomerAndMerchant(customerId, merchantId);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            
            logger.debug("Database query for customer {} merchant {} returned {} offers in {}ms", 
                customerId, merchantId, results.size(), durationMs);
            
            if (durationMs > 1000) {
                logger.warn("Slow database query detected: customer {} merchant {} took {}ms", 
                    customerId, merchantId, durationMs);
            }
            
            return results;
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("Database query failed for customer {} merchant {} after {}ms: {}", 
                customerId, merchantId, durationMs, e.getMessage());
            throw e;
        }
    }

    /**
     * Redis Fetch with fallback to database.
     * Issue #2: Added fallback mechanism when Redis is unavailable or key not found
     * Issue #8: Added performance monitoring with metrics
     */
    public String getOffersFromRedis(Integer customerId, Integer merchantId) {
        String redisKey = String.format("customer_offers:%d:%d", customerId, merchantId);
        long startTime = System.nanoTime();
        
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            
            if (cachedValue != null && !cachedValue.isBlank()) {
                logger.debug("Redis cache HIT for key {} in {}ms", redisKey, durationMs);
                return cachedValue;
            }
            
            logger.debug("Redis cache MISS for key {} in {}ms, falling back to database", redisKey, durationMs);
            
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.warn("Redis connection failed for key {} after {}ms: {}. Falling back to database.", 
                redisKey, durationMs, e.getMessage());
        }
        
        // Issue #2: Fallback to database when Redis is down or key not found
        return fallbackToDatabase(customerId, merchantId);
    }

    /**
     * Fallback method: Convert database results to JSON format matching Redis schema
     * Issue #2: Ensures graceful degradation when Redis is unavailable
     */
    private String fallbackToDatabase(Integer customerId, Integer merchantId) {
        long startTime = System.nanoTime();
        try {
            long dbQueryStart = System.nanoTime();
            List<OfferEvaluationResponse> offers = dbRepository.findActiveOffersForCustomerAndMerchant(customerId, merchantId);
            long dbQueryMs = (System.nanoTime() - dbQueryStart) / 1_000_000;
            
            if (offers.isEmpty()) {
                logger.debug("Fallback database query returned no offers for customer {} merchant {}", 
                    customerId, merchantId);
                return null;
            }
            
            // Convert to JSON array format matching Redis schema
            String jsonArray = objectMapper.writeValueAsString(offers);
            long totalMs = (System.nanoTime() - startTime) / 1_000_000;
            
            logger.info("Fallback database query for customer {} merchant {} returned {} offers (DB: {}ms, Total: {}ms)", 
                customerId, merchantId, offers.size(), dbQueryMs, totalMs);
            
            return jsonArray;
            
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("Fallback database query failed for customer {} merchant {} after {}ms: {}", 
                customerId, merchantId, durationMs, e.getMessage());
            throw new RuntimeException("Failed to retrieve offers from both cache and database", e);
        }
    }
}

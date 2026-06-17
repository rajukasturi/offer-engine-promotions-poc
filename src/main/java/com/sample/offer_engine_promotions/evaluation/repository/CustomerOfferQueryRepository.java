package com.sample.offer_engine_promotions.evaluation.repository;

import com.sample.offer_engine_promotions.evaluation.model.OfferEvaluationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CustomerOfferQueryRepository {

    private static final Logger logger = LoggerFactory.getLogger(CustomerOfferQueryRepository.class);
    private final JdbcClient jdbcClient;

    public CustomerOfferQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Issue #8: Added performance logging to detect slow queries and result set sizes
     */
    public List<OfferEvaluationResponse> findActiveOffersForCustomerAndMerchant(Integer customerId, Integer merchantId) {
        String sql = """
            SELECT 
                o.offer_id AS offerId, 
                o.reward_type AS rewardType, 
                o.reward_value AS rewardValue, 
                o.min_spend AS minSpend, 
                o.max_cap AS maxCap
            FROM customer_offers co
            JOIN offers o ON co.offer_id = o.offer_id
            WHERE co.customer_id = :customerId 
              AND co.merchant_id = :merchantId 
              AND co.status = 'AVAILABLE'
        """;

        long startTime = System.nanoTime();
        try {
            List<OfferEvaluationResponse> results = jdbcClient.sql(sql)
                    .param("customerId", customerId)
                    .param("merchantId", merchantId)
                    .query(OfferEvaluationResponse.class)
                    .list();
            
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.debug("Query for customer {} merchant {} returned {} offers in {}ms", 
                customerId, merchantId, results.size(), durationMs);
            
            if (durationMs > 1000) {
                logger.warn("SLOW QUERY DETECTED: customer {} merchant {} took {}ms", 
                    customerId, merchantId, durationMs);
            }
            
            return results;
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("Query failed for customer {} merchant {} after {}ms: {}", 
                customerId, merchantId, durationMs, e.getMessage(), e);
            throw e;
        }
    }
}
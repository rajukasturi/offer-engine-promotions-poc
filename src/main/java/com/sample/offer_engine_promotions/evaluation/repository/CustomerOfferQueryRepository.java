package com.sample.offer_engine_promotions.evaluation.repository;

import com.sample.offer_engine_promotions.evaluation.model.OfferEvaluationResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CustomerOfferQueryRepository {

    private final JdbcClient jdbcClient;

    public CustomerOfferQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

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

        return jdbcClient.sql(sql)
                .param("customerId", customerId)
                .param("merchantId", merchantId)
                .query(OfferEvaluationResponse.class)
                .list();
    }
}
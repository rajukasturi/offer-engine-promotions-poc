package com.sample.offer_engine_promotions.evaluation.service;

import com.sample.offer_engine_promotions.evaluation.model.OfferEvaluationResponse;
import com.sample.offer_engine_promotions.evaluation.repository.CustomerOfferQueryRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OfferEvaluationService {

    private final CustomerOfferQueryRepository dbRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public OfferEvaluationService(CustomerOfferQueryRepository dbRepository, StringRedisTemplate stringRedisTemplate) {
        this.dbRepository = dbRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
     * Database Fetch: Executed via pure Spring JDBC Client.
     */
    @Transactional(readOnly = true)
    public List<OfferEvaluationResponse> getOffersFromDatabase(Integer customerId, Integer merchantId) {
        return dbRepository.findActiveOffersForCustomerAndMerchant(customerId, merchantId);
    }

    /**
     * Redis Fetch: Extreme performance JSON pass-through.
     */
    public String getOffersFromRedis(Integer customerId, Integer merchantId) {
        String redisKey = String.format("customer_offers:%d:%d", customerId, merchantId);

        return stringRedisTemplate.opsForValue().get(redisKey);
    }
}

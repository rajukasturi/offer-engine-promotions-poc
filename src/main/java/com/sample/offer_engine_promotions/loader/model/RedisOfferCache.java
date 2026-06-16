package com.sample.offer_engine_promotions.loader.model;

import java.math.BigDecimal;

public record RedisOfferCache(
        Integer offerId,
        String rewardType,
        BigDecimal rewardValue,
        BigDecimal minSpend,
        BigDecimal maxCap
) {}

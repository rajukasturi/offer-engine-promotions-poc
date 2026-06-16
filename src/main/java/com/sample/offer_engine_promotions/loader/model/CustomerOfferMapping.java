package com.sample.offer_engine_promotions.loader.model;

public record CustomerOfferMapping(
        Integer customerId,
        Integer merchantId,
        RedisOfferCache cacheData
) {}
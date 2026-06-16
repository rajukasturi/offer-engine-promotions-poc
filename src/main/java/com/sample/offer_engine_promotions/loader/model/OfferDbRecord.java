package com.sample.offer_engine_promotions.loader.model;

import java.math.BigDecimal;

public record OfferDbRecord(
        Integer offerId,
        Integer merchantId,
        String rewardType,
        BigDecimal rewardValue,
        BigDecimal minSpend,
        BigDecimal maxCap
) {}

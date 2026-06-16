package com.sample.offer_engine_promotions.loader.model;

import java.math.BigDecimal;

public record OfferCsv(
        Integer merchantId,
        String rewardType,
        BigDecimal rewardValue,
        BigDecimal minSpend,
        BigDecimal maxCap,
        String startDate,
        String endDate
) {}

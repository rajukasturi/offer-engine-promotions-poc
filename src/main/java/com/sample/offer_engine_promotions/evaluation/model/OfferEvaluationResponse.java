package com.sample.offer_engine_promotions.evaluation.model;

import java.math.BigDecimal;

public record OfferEvaluationResponse(
        Integer offerId,
        String rewardType,
        BigDecimal rewardValue,
        BigDecimal minSpend,
        BigDecimal maxCap
) {}

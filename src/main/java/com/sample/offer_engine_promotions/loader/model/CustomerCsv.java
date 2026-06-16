package com.sample.offer_engine_promotions.loader.model;

import java.math.BigDecimal;

public record CustomerCsv(
        String name,
        String email,
        BigDecimal walletBalance
) {}
package com.sample.offer_engine_promotions.generator.model;

public record GenerationResult(
        String merchantFilePath,
        String offerFilePath,
        String customerFilePath
) {}

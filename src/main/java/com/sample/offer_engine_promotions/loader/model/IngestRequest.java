package com.sample.offer_engine_promotions.loader.model;

public record IngestRequest(
        String merchantFilePath,
        String customerFilePath,
        String offerFilePath
) {
}
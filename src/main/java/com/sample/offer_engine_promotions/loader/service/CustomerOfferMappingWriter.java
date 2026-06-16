package com.sample.offer_engine_promotions.loader.service;

import com.sample.offer_engine_promotions.loader.model.CustomerOfferMapping;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CustomerOfferMappingWriter implements ItemWriter<List<CustomerOfferMapping>> {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public CustomerOfferMappingWriter(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void write(Chunk<? extends List<CustomerOfferMapping>> chunk) throws Exception {

        // 1. Flatten the Chunk
        List<CustomerOfferMapping> flatList = new ArrayList<>();
        for (List<CustomerOfferMapping> list : chunk.getItems()) {
            flatList.addAll(list);
        }

        if (flatList.isEmpty()) {
            return;
        }

        // 2. High-Speed Batch Insert into PostgreSQL (Unchanged)
        String sql = "INSERT INTO customer_offers (customer_id, offer_id, merchant_id, status, progress, assigned_at) " +
                "VALUES (?, ?, ?, 'AVAILABLE', 0.00, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (customer_id, offer_id) DO NOTHING";

        jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                CustomerOfferMapping mapping = flatList.get(i);
                ps.setInt(1, mapping.customerId());
                ps.setInt(2, mapping.cacheData().offerId());
                ps.setInt(3, mapping.merchantId());
            }

            @Override
            public int getBatchSize() {
                return flatList.size();
            }
        });

        // 3. Group by Redis Key to construct JSON Arrays
        Map<String, List<CustomerOfferMapping>> groupedForRedis = flatList.stream()
                .collect(Collectors.groupingBy(
                        m -> String.format("customer_offers:%d:%d", m.customerId(), m.merchantId())
                ));

        // 4. High-Speed Pipeline Insert into Redis
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {

            for (Map.Entry<String, List<CustomerOfferMapping>> entry : groupedForRedis.entrySet()) {
                String redisKey = entry.getKey();
                List<CustomerOfferMapping> mappings = entry.getValue();

                // Manually construct the JSON Array string for maximum speed (Bypassing Jackson)
                StringBuilder jsonArrayBuilder = new StringBuilder("[");

                for (int i = 0; i < mappings.size(); i++) {
                    CustomerOfferMapping mapping = mappings.get(i);

                    // Aligned keys with Database DTO (rewardType, rewardValue)
                    String jsonObject = String.format(
                            "{\"offerId\":%d,\"rewardType\":\"%s\",\"rewardValue\":%s,\"minSpend\":%s,\"maxCap\":%s}",
                            mapping.cacheData().offerId(),
                            mapping.cacheData().rewardType(),
                            mapping.cacheData().rewardValue(),
                            mapping.cacheData().minSpend(),
                            mapping.cacheData().maxCap() == null ? "null" : mapping.cacheData().maxCap()
                    );

                    jsonArrayBuilder.append(jsonObject);

                    // Add comma if it's not the last element
                    if (i < mappings.size() - 1) {
                        jsonArrayBuilder.append(",");
                    }
                }
                jsonArrayBuilder.append("]");

                byte[] rawKey = redisTemplate.getStringSerializer().serialize(redisKey);
                byte[] rawValue = redisTemplate.getStringSerializer().serialize(jsonArrayBuilder.toString());

                connection.stringCommands().set(rawKey, rawValue);
            }
            return null;
        });
    }
}
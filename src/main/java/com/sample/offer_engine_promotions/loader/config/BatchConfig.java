package com.sample.offer_engine_promotions.loader.config;

import com.sample.offer_engine_promotions.loader.model.*;
import com.sample.offer_engine_promotions.loader.service.CustomerOfferMappingWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.RecordFieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;

@Configuration
public class BatchConfig {

    private static final int CSV_CHUNK_SIZE = 10000;
    private static final int MAPPING_CSV_CHUNK_SIZE = 200;

    // ==========================================================
    // STEP 1: MERCHANT INGESTION
    // ==========================================================
    @Bean
    @StepScope
    public FlatFileItemReader<MerchantCsv> merchantReader(@Value("#{jobParameters['merchantFilePath']}") String path) {
        return new FlatFileItemReaderBuilder<MerchantCsv>().name("merchantReader").resource(new FileSystemResource(path)).linesToSkip(1).delimited().names("businessName", "mccCode").fieldSetMapper(new RecordFieldSetMapper<>(MerchantCsv.class)).build();
    }

    @Bean
    public JdbcBatchItemWriter<MerchantCsv> merchantWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<MerchantCsv>()
                .dataSource(dataSource)
                .sql("INSERT INTO merchants (business_name, mcc_code, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)")
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setString(1, item.businessName());
                    ps.setString(2, item.mccCode());
                }).build();
    }

    @Bean
    public Step merchantStep(JobRepository jobRepository, PlatformTransactionManager tx, FlatFileItemReader<MerchantCsv> reader, JdbcBatchItemWriter<MerchantCsv> writer) {
        return new StepBuilder("merchantStep", jobRepository).<MerchantCsv, MerchantCsv>chunk(CSV_CHUNK_SIZE, tx).reader(reader).writer(writer).build();
    }

    // ==========================================================
    // STEP 2: CUSTOMER INGESTION
    // ==========================================================
    @Bean
    @StepScope
    public FlatFileItemReader<CustomerCsv> customerReader(@Value("#{jobParameters['customerFilePath']}") String path) {
        return new FlatFileItemReaderBuilder<CustomerCsv>().name("customerReader").resource(new FileSystemResource(path)).linesToSkip(1).delimited().names("name", "email", "walletBalance").fieldSetMapper(new RecordFieldSetMapper<>(CustomerCsv.class)).build();
    }

    @Bean
    public JdbcBatchItemWriter<CustomerCsv> customerWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CustomerCsv>()
                .dataSource(dataSource)
                .sql("INSERT INTO customers (name, email, wallet_balance, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (email) DO NOTHING")
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setString(1, item.name());
                    ps.setString(2, item.email());
                    ps.setBigDecimal(3, item.walletBalance());
                }).build();
    }

    @Bean
    public Step customerStep(JobRepository jobRepository, PlatformTransactionManager tx, FlatFileItemReader<CustomerCsv> reader, JdbcBatchItemWriter<CustomerCsv> writer) {
        return new StepBuilder("customerStep", jobRepository).<CustomerCsv, CustomerCsv>chunk(CSV_CHUNK_SIZE, tx).reader(reader).writer(writer).build();
    }

    // ==========================================================
    // STEP 3: OFFER INGESTION
    // ==========================================================
    @Bean
    @StepScope
    public FlatFileItemReader<OfferCsv> offerReader(@Value("#{jobParameters['offerFilePath']}") String path) {
        return new FlatFileItemReaderBuilder<OfferCsv>().name("offerReader").resource(new FileSystemResource(path)).linesToSkip(1).delimited().names("merchantId", "rewardType", "rewardValue", "minSpend", "maxCap", "startDate", "endDate").fieldSetMapper(new RecordFieldSetMapper<>(OfferCsv.class)).build();
    }

    @Bean
    public JdbcBatchItemWriter<OfferCsv> offerWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<OfferCsv>()
                .dataSource(dataSource)
                .sql("INSERT INTO offers (merchant_id, reward_type, reward_value, min_spend, max_cap, start_date, end_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)")
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setInt(1, item.merchantId());
                    ps.setString(2, item.rewardType());
                    ps.setBigDecimal(3, item.rewardValue());
                    ps.setBigDecimal(4, item.minSpend());
                    ps.setBigDecimal(5, item.maxCap() != null ? item.maxCap() : BigDecimal.ZERO);
                    ps.setTimestamp(6, Timestamp.from(ZonedDateTime.parse(item.startDate()).toInstant()));
                    ps.setTimestamp(7, item.endDate() != null && !item.endDate().isEmpty() ? Timestamp.from(ZonedDateTime.parse(item.endDate()).toInstant()) : null);
                }).build();
    }

    @Bean
    public Step offerStep(JobRepository jobRepository, PlatformTransactionManager tx, FlatFileItemReader<OfferCsv> reader, JdbcBatchItemWriter<OfferCsv> writer) {
        return new StepBuilder("offerStep", jobRepository).<OfferCsv, OfferCsv>chunk(CSV_CHUNK_SIZE, tx).reader(reader).writer(writer).build();
    }

    // ==========================================================
    // STEP 4: MAPPING GENERATION (REDIS & DB) - OPTIMIZED
    // ==========================================================

    @Bean
    public JdbcCursorItemReader<CustomerDbRecord> customerDbReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<CustomerDbRecord>()
                .dataSource(dataSource)
                .name("customerDbReader")
                .sql("SELECT customer_id, email FROM customers")
                .rowMapper(new DataClassRowMapper<>(CustomerDbRecord.class))
                .build();
    }

    // Temporary record strictly to hold references in memory efficiently
    private record PrecomputedOffer(Integer merchantId, RedisOfferCache cache) {}

    @Bean
    @StepScope
    public ItemProcessor<CustomerDbRecord, List<CustomerOfferMapping>> customerOfferProcessor(
            @Value("#{jobParameters['executionId']}") String executionId,
            JdbcTemplate jdbcTemplate) {

        // 1. Fetch offers
        List<OfferDbRecord> rawOffers = jdbcTemplate.query(
                "SELECT offer_id, merchant_id, reward_type, reward_value, min_spend, max_cap FROM offers",
                new DataClassRowMapper<>(OfferDbRecord.class)
        );

        // 2. Map to Precomputed objects ONCE.
        // This ensures all customers share the exact same RedisOfferCache object references in memory.
        List<PrecomputedOffer> precomputedOffers = rawOffers.stream()
                .map(offer -> new PrecomputedOffer(
                        offer.merchantId(),
                        new RedisOfferCache(offer.offerId(), offer.rewardType(), offer.rewardValue(), offer.minSpend(), offer.maxCap())
                )).toList();

        // 3. Process the smaller chunk (e.g., 600 customers * 7,500 offers = 4.5 million items per chunk instead of 75M)
        return customer -> precomputedOffers.stream()
                .map(po -> new CustomerOfferMapping(customer.customerId(), po.merchantId(), po.cache()))
                .toList();
    }

    @Bean
    public Step cacheAndMapStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                JdbcCursorItemReader<CustomerDbRecord> reader,
                                ItemProcessor<CustomerDbRecord, List<CustomerOfferMapping>> processor,
                                CustomerOfferMappingWriter mappingWriter) {

        return new StepBuilder("cacheAndMapStep", jobRepository)
                // Use the drastically smaller chunk size here!
                .<CustomerDbRecord, List<CustomerOfferMapping>>chunk(MAPPING_CSV_CHUNK_SIZE, tx)
                .reader(reader)
                .processor(processor)
                .writer(mappingWriter)
                .build();
    }

    // ==========================================================
    // THE MASTER JOB PIPELINE
    // ==========================================================
    @Bean
    public Job dataPipelineJob(JobRepository jobRepository, Step merchantStep, Step customerStep, Step offerStep, Step cacheAndMapStep) {
        return new JobBuilder("dataPipelineJob", jobRepository)
                .start(merchantStep)
                .next(customerStep)
                .next(offerStep)
                .next(cacheAndMapStep)
                .build();
    }
}
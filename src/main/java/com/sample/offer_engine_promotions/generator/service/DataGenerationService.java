package com.sample.offer_engine_promotions.generator.service;

import com.sample.offer_engine_promotions.generator.model.GenerationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DataGenerationService {

    private static final String[] REWARD_TYPES = {"PERCENTAGE", "FLAT"};
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Value("${file-directory}")
    private String windowBasePath;

    public GenerationResult generateSampleData(int customerCount) throws IOException {

        Path baseDir = Paths.get(windowBasePath);
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        // Configuration for generation
        int merchantCount = 500; // Generate 1,000 standard merchants

        // 1. Generate Merchants
        Path merchantFile = baseDir.resolve("merchants.csv");
        generateMerchants(merchantFile, merchantCount);

        // 2. Generate Offers
        Path offerFile = baseDir.resolve("offers.csv");
        generateOffers(offerFile, merchantCount);

        // 3. Generate Customers
        Path customerFile = baseDir.resolve("customers.csv");
        generateCustomers(customerFile, customerCount);

        return new GenerationResult(
                merchantFile.toAbsolutePath().toString(),
                offerFile.toAbsolutePath().toString(),
                customerFile.toAbsolutePath().toString()
        );
    }

    private void generateMerchants(Path filePath, int count) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("businessName,mccCode\n"); // Header

            for (int i = 1; i <= count; i++) {
                String name = "Merchant_Brand_" + i;
                String mcc = String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
                writer.write(name + "," + mcc + "\n");
            }
        }
    }

    private void generateOffers(Path filePath, int merchantCount) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("merchantId,rewardType,rewardValue,minSpend,maxCap,startDate,endDate\n"); // Header

            ZonedDateTime now = ZonedDateTime.now();
            String startDate = now.minusDays(10).format(ISO_FORMAT);
            String endDate = now.plusDays(30).format(ISO_FORMAT);

            for (int merchantId = 1; merchantId <= merchantCount; merchantId++) {

                // Randomly assign between 5 and 10 offers to this specific merchant
                int randomOffersForThisMerchant = ThreadLocalRandom.current().nextInt(2, 5);

                for (int j = 0; j < randomOffersForThisMerchant; j++) {
                    String type = REWARD_TYPES[ThreadLocalRandom.current().nextInt(REWARD_TYPES.length)];

                    // Logic: If flat, $5 to $20. If percentage, 2% to 15%.
                    double rewardValue = type.equals("FLAT") ?
                            ThreadLocalRandom.current().nextDouble(5.0, 20.0) :
                            ThreadLocalRandom.current().nextDouble(2.0, 15.0);

                    double minSpend = ThreadLocalRandom.current().nextDouble(10.0, 50.0);
                    double maxCap = type.equals("PERCENTAGE") ? ThreadLocalRandom.current().nextDouble(10.0, 50.0) : 0.0;

                    String maxCapStr = maxCap > 0 ? String.format("%.2f", maxCap) : "";

                    writer.write(String.format("%d,%s,%.2f,%.2f,%s,%s,%s\n",
                            merchantId, type, rewardValue, minSpend, maxCapStr, startDate, endDate));
                }
            }
        }
    }

    private void generateCustomers(Path filePath, int count) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("name,email,walletBalance\n"); // Header

            for (int i = 1; i <= count; i++) {
                String name = "Customer_" + i;
                String email = "user" + i + "_" + System.currentTimeMillis() + "@mockdata.com";
                double balance = ThreadLocalRandom.current().nextDouble(0.0, 100.0);

                writer.write(String.format("%s,%s,%.2f\n", name, email, balance));
            }
        }
    }
}
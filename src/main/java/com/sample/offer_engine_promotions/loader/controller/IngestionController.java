package com.sample.offer_engine_promotions.loader.controller;

import com.sample.offer_engine_promotions.loader.model.IngestRequest;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestionController {

    private final JobLauncher jobLauncher;
    private final Job dataPipelineJob;

    public IngestionController(JobLauncher jobLauncher, Job dataPipelineJob) {
        this.jobLauncher = jobLauncher;
        this.dataPipelineJob = dataPipelineJob;
    }

    @PostMapping("/local-files")
    public ResponseEntity<String> triggerLocalIngestion(@RequestBody IngestRequest request) {

        String executionId = UUID.randomUUID().toString();

        // Run the batch job asynchronously using a Virtual Thread
        Thread.ofVirtual().start(() -> {
            try {
                jobLauncher.run(dataPipelineJob, new JobParametersBuilder()
                        .addString("merchantFilePath", request.merchantFilePath())
                        .addString("customerFilePath", request.customerFilePath())
                        .addString("offerFilePath", request.offerFilePath())
                        .addString("executionId", executionId) // Ensures uniqueness of job instance
                        .toJobParameters());
            } catch (Exception e) {
                // Log failure to your logging system
                System.err.println("Batch Job Failed: " + e.getMessage());
            }
        });

        return ResponseEntity.accepted().body("Batch Pipeline initiated successfully. Tracking ID: " + executionId);
    }
}
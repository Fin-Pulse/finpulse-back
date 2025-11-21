package com.example.aggregationservice.config;

import com.example.aggregationservice.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheInitializer implements ApplicationRunner {

    private final ProductSyncService productSyncService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Initializing product cache...");
            productSyncService.loadProductsToCacheOnStartup();
            log.info("Product cache initialized");
        } catch (Exception e) {
            log.error("Failed to initialize product cache", e);
        }
    }
}

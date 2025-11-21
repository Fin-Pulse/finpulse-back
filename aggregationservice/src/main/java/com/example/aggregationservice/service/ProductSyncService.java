package com.example.aggregationservice.service;

import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.model.BankProduct;
import com.example.aggregationservice.repository.BankProductRepository;
import com.example.aggregationservice.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncService {

    private final BankApiClient bankApiClient;
    private final BankRepository bankRepository;
    private final BankProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_TEMPLATE = "bank_products:%s"; // bank_products:{bankId}
    private static final long REDIS_TTL_HOURS = 24;

    /**
     * Update products for all banks.
     */
    @Transactional
    public void updateAllBankProducts() {
        List<Bank> banks = bankRepository.findAll();
        for (Bank bank : banks) {
            try {
                updateProductsForBank(bank);
            } catch (Exception e) {
                log.error("Failed to update products for bank {}: {}", bank.getCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * Update products for specific bank entity.
     */
    @Transactional
    public void updateProductsForBank(Bank bank) {
        log.info("Starting product sync for bank: {} ({})", bank.getName(), bank.getCode());
        Optional<List<Map<String, Object>>> maybeProducts = bankApiClient.fetchProducts(bank);

        if (maybeProducts.isEmpty()) {
            log.warn("No products received from bank {}", bank.getCode());
            return;
        }

        List<Map<String, Object>> rawProducts = maybeProducts.get();
        syncBankProducts(bank, rawProducts);

        // После успешного сохранения — кешируем
        cacheBankProducts(bank.getId());
        log.info("Product sync completed for bank: {}", bank.getCode());
    }

    /**
     * Update products for a specific bankId (used by handler when bankId passed via task).
     */
    @Transactional
    public void updateProductsForBankId(UUID bankId) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new RuntimeException("Bank not found: " + bankId));
        updateProductsForBank(bank);
    }

    /**
     * Insert/update DB records based on raw bank response.
     */
    private void syncBankProducts(Bank bank, List<Map<String, Object>> products) {
        for (Map<String, Object> raw : products) {
            try {
                // Поля в ответе банков были: productId, productType, productName, description, interestRate, minAmount, maxAmount, termMonths
                String externalProductId = Optional.ofNullable(raw.get("productId"))
                        .map(Object::toString).orElseGet(() -> Optional.ofNullable(raw.get("id")).map(Object::toString).orElse(null));

                if (externalProductId == null) {
                    log.warn("Skipping product without id from bank {}", bank.getCode());
                    continue;
                }

                BankProduct entity = productRepository.findByBankIdAndProductId(bank.getId(), externalProductId)
                        .orElseGet(() -> {
                            BankProduct p = new BankProduct();
                            p.setBankId(bank.getId());
                            p.setProductId(externalProductId);
                            p.setCreatedAt(Instant.now());
                            return p;
                        });

                entity.setProductType(asString(raw.get("productType"), raw.get("type")));
                entity.setProductName(asString(raw.get("productName"), raw.get("name")));
                entity.setDescription(asString(raw.get("description"), raw.get("desc")));
                entity.setInterestRate(parseDecimal(raw.get("interestRate")));
                entity.setMinAmount(parseDecimal(raw.get("minAmount")));
                entity.setMaxAmount(parseDecimal(raw.get("maxAmount")));
                entity.setTermMonths(parseInteger(raw.get("termMonths")));
                entity.setIsActive(true);
                entity.setLastSyncedAt(Instant.now());
                entity.setUpdatedAt(Instant.now());

                productRepository.save(entity);
            } catch (Exception e) {
                log.error("Error syncing product for bank {}: {}", bank.getCode(), e.getMessage(), e);
            }
        }
    }

    private String asString(Object... candidates) {
        for (Object c : candidates) {
            if (c != null) return c.toString();
        }
        return null;
    }

    private BigDecimal parseDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            log.warn("Failed to parse decimal value: {}", value);
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            log.warn("Failed to parse integer value: {}", value);
            return null;
        }
    }

    /**
     * Cache products for a bank into Redis.
     */
    public void cacheBankProducts(UUID bankId) {
        List<BankProduct> products = productRepository.findByBankId(bankId);
        String key = String.format(REDIS_KEY_TEMPLATE, bankId.toString());
        try {
            redisTemplate.opsForValue().set(key, products, REDIS_TTL_HOURS, TimeUnit.HOURS);
            log.info("Cached {} products for bank {}", products.size(), bankId);
        } catch (Exception e) {
            log.error("Failed to cache products for bank {}: {}", bankId, e.getMessage(), e);
        }
    }

    /**
     * Load all banks' products into cache on startup (if DB has data).
     */
    public void loadProductsToCacheOnStartup() {
        List<Bank> banks = bankRepository.findAll();

        boolean isDbEmpty = productRepository.count() == 0;

        for (Bank bank : banks) {
            try {
                if (isDbEmpty) {
                    log.info("DB is empty — performing full product sync from bank {}", bank.getCode());
                    updateProductsForBank(bank);   // <-- Важно: вызов API + запись в БД + кеширование
                } else {
                    log.info("DB has products — only caching for bank {}", bank.getCode());
                    cacheBankProducts(bank.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to initialize products for bank {}: {}", bank.getId(), e.getMessage());
            }
        }
    }

}

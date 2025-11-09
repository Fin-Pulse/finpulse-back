package com.example.notificationservice.service;

import com.example.notificationservice.dto.ForecastDto;
import com.example.notificationservice.model.UserForecast;
import com.example.notificationservice.repository.UserForecastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ForecastService {

    private final UserForecastRepository userForecastRepository;

    public ForecastDto getLatestForecast(UUID userId) {
        try {

            Optional<UserForecast> forecastOpt = userForecastRepository.findLatestByUserId(userId);

            if (forecastOpt.isEmpty()) {
                return null;
            }

            UserForecast forecast = forecastOpt.get();

            return convertToDto(forecast);

        } catch (Exception e) {
            log.error("Ошибка получения прогноза для пользователя {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    private ForecastDto convertToDto(UserForecast forecast) {
        return ForecastDto.builder()
                .forecastAmount(forecast.getForecastAmount())
                .confidenceMin(forecast.getConfidenceMin())
                .confidenceMax(forecast.getConfidenceMax())
                .changePercentage(forecast.getChangePercentage())
                .lastWeekAmount(forecast.getLastWeekAmount())
                .forecastMethod(forecast.getForecastMethod())
                .forecastWeekStart(forecast.getForecastWeekStart())
                .fullForecastData(forecast.getFullForecastData())
                .chartUrls(forecast.getChartUrls())
                .build();
    }

    public void enrichChartUrlsWithFullPath(ForecastDto forecast, String minioPublicUrl) {
        if (forecast == null || forecast.getChartUrls() == null || minioPublicUrl == null) {
            return;
        }

        forecast.getChartUrls().forEach((key, path) -> {
            if (path != null && !path.startsWith("http")) {
                String fullUrl = minioPublicUrl.endsWith("/")
                        ? minioPublicUrl + path
                        : minioPublicUrl + "/" + path;
                forecast.getChartUrls().put(key, fullUrl);
            }
        });
    }


    @Transactional
    public UserForecast saveForecast(UserForecast forecast) {
        return userForecastRepository.save(forecast);
    }
}
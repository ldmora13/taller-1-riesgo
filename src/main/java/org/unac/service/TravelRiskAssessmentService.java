package org.unac.service;

import org.unac.exception.ExternalServiceException;
import org.unac.client.CountryClient;
import org.unac.client.HolidayClient;
import org.unac.client.WeatherClient;
import org.unac.model.*;

import java.time.LocalDate;
import java.util.List;

public class TravelRiskAssessmentService {

    private final WeatherClient weatherClient;
    private final HolidayClient holidayClient;
    private final CountryClient countryClient;

    public TravelRiskAssessmentService(
            WeatherClient weatherClient,
            HolidayClient holidayClient,
            CountryClient countryClient
    ) {
        this.weatherClient = weatherClient;
        this.holidayClient = holidayClient;
        this.countryClient = countryClient;
    }

    public TravelRiskResponse assessRisk(TravelRequest request) {

        WeatherData weather = weatherClient.getWeather(request.countryCode);
        List<Holiday> holidays = holidayClient.getHolidays(request.countryCode, request.travelDate.getYear());
        CountryData country = countryClient.getCountry(request.countryCode);

        validate(weather, holidays, country);

        boolean isExpert = request.travelerExperienceYears > 10;

        // HIGH PRIORITY
        if (!isExpert && weather.temperatureMax < 0) {
            return buildResponse(RiskLevel.HIGH_RISK, request, "Extreme sub-zero temperatures detected");
        }

        if (isInsufficientBudget(request.budget, country.population)) {
            return buildResponse(RiskLevel.HIGH_RISK, request, "Insufficient budget for the destination");
        }

        if (hasHolidayCluster(holidays, request.travelDate)) {
            return buildResponse(RiskLevel.HIGH_RISK, request, "High concentration of holidays during the week of travel");
        }

        if (country.population > 100_000_000 && request.travelerExperienceYears < 2) {
            return buildResponse(RiskLevel.HIGH_RISK, request, "Destination with high population density and low traveler experience");
        }

        // MEDIUM PRIORITY
        if (!isExpert && weather.precipitationProbability > 80) {
            return buildResponse(RiskLevel.MEDIUM_RISK, request, "High probability of rain during the trip");
        }

        if (isHolidayOnDate(holidays, request.travelDate)) {
            return buildResponse(RiskLevel.MEDIUM_RISK, request, "The trip coincides with a national holiday");
        }

        if (isLanguageBarrier(country.languages)) {
            return buildResponse(RiskLevel.MEDIUM_RISK, request, "The language of the destination may present a barrier");
        }

        return buildResponse(RiskLevel.SAFE, request, "Optimal conditions for travel");
    }

    private void validate(WeatherData weather, List<Holiday> holidays, CountryData country) {
        if (weather == null
                || holidays == null
                || holidays.isEmpty()
                || country == null
                || country.languages == null
                || country.languages.isEmpty()) {
            throw new ExternalServiceException("External service failure");
        }
    }

    private boolean isInsufficientBudget(double budget, long population) {
        if (population < 10_000_000) return budget < 1000;
        if (population <= 100_000_000) return budget < 2000;
        return budget < 3000;
    }

    private boolean hasHolidayCluster(List<Holiday> holidays, LocalDate date) {
        long count = holidays.stream()
                .filter(h -> !h.date.isBefore(date) && !h.date.isAfter(date.plusDays(7)))
                .count();
        return count >= 3;
    }

    private boolean isHolidayOnDate(List<Holiday> holidays, LocalDate date) {
        return holidays.stream().anyMatch(h -> h.date.equals(date));
    }

    private boolean isLanguageBarrier(List<String> languages) {
        return languages.stream()
                .map(String::toLowerCase)
                .noneMatch(lang ->
                        lang.contains("spanish") ||
                                lang.contains("english") ||
                                lang.equals("es") ||
                                lang.equals("en")
                );
    }

    private TravelRiskResponse buildResponse(RiskLevel level, TravelRequest request, String reason) {
        TravelRiskResponse response = new TravelRiskResponse();
        response.riskLevel = level;
        response.reason = request.includeReason ? reason : null;
        return response;
    }
}

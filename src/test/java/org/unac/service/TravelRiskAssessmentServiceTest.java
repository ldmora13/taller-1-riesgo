package org.unac.service;

import org.junit.jupiter.api.Test;
import org.unac.client.CountryClient;
import org.unac.client.HolidayClient;
import org.unac.client.WeatherClient;
import org.unac.model.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import org.unac.exception.ExternalServiceException;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class TravelRiskAssessmentServiceTest {

    @Mock WeatherClient weatherClient;
    @Mock HolidayClient holidayClient;
    @Mock CountryClient countryClient;

    @InjectMocks
    TravelRiskAssessmentService service;

    private TravelRequest baseRequest() {
        TravelRequest r = new TravelRequest();
        r.countryCode = "CO";
        r.travelDate = LocalDate.of(2026, 5, 10);
        r.budget = 5000;
        r.travelerExperienceYears = 1;
        r.includeReason = true;
        return r;
    }

    private WeatherData weather(double temp, int rain) {
        return new WeatherData(temp, rain);
    }
    private CountryData country(long population, List<String> languages) {
        return new CountryData(population, languages);
    }

    private List<Holiday> holidays(LocalDate... dates) {
        return List.of(dates).stream().map(Holiday::new).toList();
    }

    private void mockAll(WeatherData w, List<Holiday> h, CountryData c) {
        when(weatherClient.getWeather(anyString())).thenReturn(w);
        when(holidayClient.getHolidays(anyString(), anyInt())).thenReturn(h);
        when(countryClient.getCountry(anyString())).thenReturn(c);
    }

    private void verifyAll() {
        verify(weatherClient).getWeather(anyString());
        verify(holidayClient).getHolidays(anyString(), anyInt());
        verify(countryClient).getCountry(anyString());
    }

    @Test
    void shouldPrioritizeHighOverMedium() {
        var req = baseRequest();
        mockAll(weather(-5, 90), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Extreme sub-zero temperatures detected", res.reason);
        verifyAll();
    }

    @Test
    void shouldNotTriggerRainAt80() {
        var req = baseRequest();
        mockAll(weather(20, 80), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.SAFE, res.riskLevel);
        verifyAll();
    }

    @Test
    void shouldTriggerHighWhenTemperatureMinusOne() {
        var req = baseRequest();
        mockAll(weather(-1, 0), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Extreme sub-zero temperatures detected", res.reason);
        verifyAll();
    }

    @Test
    void shouldBeSafeWhenTemperatureIsZero() {
        var req = baseRequest();
        mockAll(weather(0, 0), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.SAFE, res.riskLevel);
        assertEquals("Optimal conditions for travel", res.reason);
        verifyAll();
    }

    @Test
    void shouldTriggerMediumRiskWhenRainIs81() {
        var req = baseRequest();
        mockAll(weather(20, 81), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.MEDIUM_RISK, res.riskLevel);
        assertEquals("High probability of rain during the trip", res.reason);
        verifyAll();
    }

    @Test
    void shouldTriggerHighWhenExactlyThreeHolidays() {
        var req = baseRequest();
        // date+0, date+4, date+7 are 3 holidays in the 8-day window [date, date+7]
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(1), req.travelDate.plusDays(4), req.travelDate.plusDays(7)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("High concentration of holidays during the week of travel", res.reason);
        verifyAll();
    }

    @Test
    void shouldNotTriggerHighWhenTwoHolidays() {
        var req = baseRequest();
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(1), req.travelDate.plusDays(7)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.SAFE, res.riskLevel);
        verifyAll();
    }

    @Test
    void shouldReturnNullReasonWhenDisabled() {
        var req = baseRequest();
        req.includeReason = false;
        mockAll(weather(-5, 0), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);

        assertNull(res.reason);
        verifyAll();
    }

    @Test
    void shouldReturnOnlyOneReason() {
        var req = baseRequest();
        mockAll(weather(-5, 90), holidays(req.travelDate), country(200_000_000, List.of("japanese")));

        var res = service.assessRisk(req);

        assertNotNull(res.reason);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        verifyAll();
    }

    @Test
    void shouldThrowWhenLanguagesNull() {
        var req = baseRequest();

        when(weatherClient.getWeather(anyString()))
                .thenReturn(weather(20, 0));

        when(holidayClient.getHolidays(anyString(), anyInt()))
                .thenReturn(holidays(req.travelDate.plusDays(10)));

        when(countryClient.getCountry(anyString()))
                .thenReturn(new CountryData(1000, null));

        assertThrows(ExternalServiceException.class, () -> service.assessRisk(req));

        verify(weatherClient).getWeather(anyString());
        verify(holidayClient).getHolidays(anyString(), anyInt());
        verify(countryClient).getCountry(anyString());
    }

    @Test
    void shouldTriggerHighRiskWhenInsufficientBudgetSmallCountry() {
        var req = baseRequest();
        req.budget = 999;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(9_000_000, List.of("spanish")));
        
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Insufficient budget for the destination", res.reason);
        verifyAll();
    }

    @Test
    void shouldTriggerHighRiskWhenInsufficientBudgetMediumCountry() {
        var req = baseRequest();
        req.budget = 1999;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));
        
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Insufficient budget for the destination", res.reason);
        verifyAll();
    }

    @Test
    void shouldTriggerHighRiskWhenInsufficientBudgetLargeCountry() {
        var req = baseRequest();
        req.budget = 2999;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        verifyAll();
    }


    @Test
    void shouldReturnSafeWhenLanguageIsEnglish() {
        var req = baseRequest();

        mockAll(
                weather(20, 0),
                holidays(req.travelDate.plusDays(10)),
                country(50_000_000, List.of("English"))
        );

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.SAFE, res.riskLevel);
        verifyAll();
    }

    @Test
    void shouldReturnSafeWhenLanguageIsSpanish() {
        var req = baseRequest();

        mockAll(
                weather(20, 0),
                holidays(req.travelDate.plusDays(10)),
                country(50_000_000, List.of("spanish"))
        );

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.SAFE, res.riskLevel);
        verifyAll();
    }

    @Test
    void shouldTriggerHighRiskWhenHighPopAndLowExpBoundary() {
        var req = baseRequest();
        req.travelerExperienceYears = 0;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Destination with high population density and low traveler experience", res.reason);
        verifyAll();
    }

    @Test
    void shouldNotTriggerHighRiskWhenHighPopAndExpIsTwo() {
        var req = baseRequest();
        req.travelerExperienceYears = 2;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }

    @Test
    void shouldTriggerHighRiskWhenHighPopAndExpIsOne() {
        var req = baseRequest();
        req.travelerExperienceYears = 1;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Destination with high population density and low traveler experience", res.reason);
    }

    @Test
    void shouldTriggerHighRiskWhenBudgetExactlyCostoMinimo() {
        // population < 10M, cost 1000. budget < 1000 is HIGH. 1000 should be SAFE.
        var req = baseRequest();
        req.budget = 1000;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(9_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }

    @Test
    void shouldTriggerHighRiskWhenBudgetExactlyCostoMinimoMedium() {
        // population 10-100M, cost 2000. budget < 2000 is HIGH. 2000 should be SAFE.
        var req = baseRequest();
        req.budget = 2000;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }

    @Test
    void shouldTriggerHighRiskWhenBudgetExactlyCostoMinimoLarge() {
        // population > 100M, cost 3000. budget < 3000 is HIGH. 3000 should be SAFE.
        var req = baseRequest();
        req.budget = 3000;
        req.travelerExperienceYears = 3; // Ensure pop rule doesn't trigger
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
        assertEquals("Optimal conditions for travel", res.reason);
    }

    @Test
    void shouldTriggerHighRiskWhenBudgetIs2999Large() {
        // population > 100M, cost 3000. budget < 3000 is HIGH. 2999 should be HIGH.
        var req = baseRequest();
        req.budget = 2999;
        req.travelerExperienceYears = 3; // Ensure pop rule doesn't trigger
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Insufficient budget for the destination", res.reason);
    }

    @Test
    void shouldTriggerHighRiskWhenPopulationExactlyTenMillion() {
        // if population <= 100M, cost 2000.
        // if population < 10M, cost 1000.
        // at 10M, cost should be 2000. budget 1999 should be HIGH.
        var req = baseRequest();
        req.budget = 1999;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(10_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
    }

    @Test
    void shouldTriggerHighRiskWhenPopulationIsJustBelowTenMillion() {
        // population < 10M, cost 1000. budget 999 should be HIGH.
        var req = baseRequest();
        req.budget = 999;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(9_999_999, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
    }

    @Test
    void shouldBeSafeWhenPopulationIsJustBelowTenMillionAndBudgetIsOneThousand() {
        var req = baseRequest();
        req.budget = 1000;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(9_999_999, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }

    @Test
    void shouldTriggerHighRiskWhenPopulationExactlyOneHundredMillion() {
        // if population <= 100M, cost 2000.
        // if population > 100M, cost 3000.
        // at 100M, cost should be 2000. budget 2000 should be SAFE.
        var req = baseRequest();
        req.budget = 2000;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
        assertEquals("Optimal conditions for travel", res.reason);
    }

    @Test
    void shouldTriggerHighRiskWhenPopulationIs100M001AndBudgetIs2999() {
        // at 100,000,001, cost should be 3000. budget 2999 should be HIGH.
        var req = baseRequest();
        req.budget = 2999;
        req.travelerExperienceYears = 3; 
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Insufficient budget for the destination", res.reason);
    }

    @Test
    void shouldNotTriggerHighRiskWhenHolidaysExactlyInSevenDays() {
        // hasHolidayCluster count >= 3 in [date, date+7]
        var req = baseRequest();
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(1), req.travelDate.plusDays(4), req.travelDate.plusDays(7)), country(50_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("High concentration of holidays during the week of travel", res.reason);
    }
    
    @Test
    void shouldNotTriggerHighRiskWhenExactlyTwoHolidaysInWindow() {
        var req = baseRequest();
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(1), req.travelDate.plusDays(7)), country(50_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }
    
    @Test
    void shouldNotTriggerHighRiskWhenHolidayExactlyAfterSevenDays() {
        var req = baseRequest();
        // Use plusDays(1) instead of travelDate to avoid MEDIUM_RISK "trip coincides with national holiday"
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(1), req.travelDate.plusDays(2), req.travelDate.plusDays(8)), country(50_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }

    @Test
    void shouldNotTriggerHighRiskWhenHolidayExactlyBeforeDate() {
        var req = baseRequest();
        mockAll(weather(20, 0), holidays(req.travelDate.minusDays(1), req.travelDate.plusDays(1), req.travelDate.plusDays(2)), country(50_000_000, List.of("spanish")));
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }
    
    @Test
    void shouldThrowWhenCountryIsNull() {
        var req = baseRequest();
        when(weatherClient.getWeather(anyString())).thenReturn(weather(20, 0));
        when(holidayClient.getHolidays(anyString(), anyInt())).thenReturn(holidays(req.travelDate));
        when(countryClient.getCountry(anyString())).thenReturn(null);
        assertThrows(ExternalServiceException.class, () -> service.assessRisk(req));
    }

    @Test
    void shouldTriggerHighRiskWhenHighPopAndLowExp() {
        var req = baseRequest();
        req.travelerExperienceYears = 1;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_001, List.of("spanish")));
        
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Destination with high population density and low traveler experience", res.reason);
    }

    @Test
    void shouldNotTriggerHighRiskWhenPopIsExactlyOneHundredMillionAndExpIsOne() {
        var req = baseRequest();
        req.travelerExperienceYears = 1;
        mockAll(weather(20, 0), holidays(req.travelDate.plusDays(10)), country(100_000_000, List.of("spanish")));

        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
    }

    @Test
    void shouldIgnoreWeatherWhenExpert() {
        var req = baseRequest();
        req.travelerExperienceYears = 11;
        mockAll(weather(-10, 100), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));
        
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.SAFE, res.riskLevel);
        assertEquals("Optimal conditions for travel", res.reason);
    }

    @Test
    void shouldNotIgnoreWeatherWhenExperienceIsExactlyTen() {
        var req = baseRequest();
        req.travelerExperienceYears = 10;
        mockAll(weather(-10, 100), holidays(req.travelDate.plusDays(10)), country(50_000_000, List.of("spanish")));

        var res = service.assessRisk(req);
        assertEquals(RiskLevel.HIGH_RISK, res.riskLevel);
        assertEquals("Extreme sub-zero temperatures detected", res.reason);
    }

    @Test
    void shouldTriggerMediumRiskWhenHolidayOnDate() {
        var req = baseRequest();
        mockAll(weather(20, 0), holidays(req.travelDate), country(50_000_000, List.of("spanish")));
        
        var res = service.assessRisk(req);
        assertEquals(RiskLevel.MEDIUM_RISK, res.riskLevel);
        assertEquals("The trip coincides with a national holiday", res.reason);
    }


    @Test
    void shouldTriggerMediumRiskWhenLanguageBarrier() {
        var req = baseRequest();

        mockAll(
                weather(20, 0),
                holidays(req.travelDate.plusDays(10)),
                country(50_000_000, List.of("french"))
        );

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.MEDIUM_RISK, res.riskLevel);
        assertEquals("The language of the destination may present a barrier", res.reason);

        verifyAll();
    }

    @Test
    void shouldThrowWhenWeatherIsNull() {
        var req = baseRequest();
        when(weatherClient.getWeather(anyString())).thenReturn(null);
        assertThrows(ExternalServiceException.class, () -> service.assessRisk(req));
    }

    @Test
    void shouldThrowWhenHolidaysAreNull() {
        var req = baseRequest();
        when(weatherClient.getWeather(anyString())).thenReturn(weather(20, 0));
        when(holidayClient.getHolidays(anyString(), anyInt())).thenReturn(null);
        when(countryClient.getCountry(anyString())).thenReturn(country(50_000_000, List.of("spanish")));

        assertThrows(ExternalServiceException.class, () -> service.assessRisk(req));
    }

    @Test
    void shouldThrowWhenHolidaysEmpty() {
        var req = baseRequest();
        when(weatherClient.getWeather(anyString())).thenReturn(weather(20, 0));
        when(holidayClient.getHolidays(anyString(), anyInt())).thenReturn(List.of());
        assertThrows(ExternalServiceException.class, () -> service.assessRisk(req));
    }

    @Test
    void shouldThrowWhenLanguagesAreEmpty() {
        var req = baseRequest();
        when(weatherClient.getWeather(anyString())).thenReturn(weather(20, 0));
        when(holidayClient.getHolidays(anyString(), anyInt())).thenReturn(holidays(req.travelDate.plusDays(10)));
        when(countryClient.getCountry(anyString())).thenReturn(country(50_000_000, List.of()));

        assertThrows(ExternalServiceException.class, () -> service.assessRisk(req));
    }

    @Test
    void shouldReturnSafeWhenLanguageCodeIsEs() {
        var req = baseRequest();
        mockAll(
                weather(20, 0),
                holidays(req.travelDate.plusDays(10)),
                country(50_000_000, List.of("es"))
        );

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.SAFE, res.riskLevel);
        assertEquals("Optimal conditions for travel", res.reason);
        verifyAll();
    }

    @Test
    void shouldReturnSafeWhenLanguageCodeIsEn() {
        var req = baseRequest();
        mockAll(
                weather(20, 0),
                holidays(req.travelDate.plusDays(10)),
                country(50_000_000, List.of("en"))
        );

        var res = service.assessRisk(req);

        assertEquals(RiskLevel.SAFE, res.riskLevel);
        assertEquals("Optimal conditions for travel", res.reason);
        verifyAll();
    }
}

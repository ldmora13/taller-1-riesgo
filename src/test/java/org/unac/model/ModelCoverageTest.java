package org.unac.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ModelCoverageTest {

    @Test
    void testTravelRequestCoverage() {
        TravelRequest request = new TravelRequest();
        request.countryCode = "CO";
        request.travelDate = LocalDate.now();
        request.budget = 1000.0;
        request.travelerExperienceYears = 5;
        request.includeReason = true;

        assertEquals("CO", request.countryCode);
        assertNotNull(request.travelDate);
        assertEquals(1000.0, request.budget);
        assertEquals(5, request.travelerExperienceYears);
        assertTrue(request.includeReason);
    }

    @Test
    void testTravelRiskResponseCoverage() {
        TravelRiskResponse response = new TravelRiskResponse();
        response.riskLevel = RiskLevel.SAFE;
        response.reason = "Test reason";

        assertEquals(RiskLevel.SAFE, response.riskLevel);
        assertEquals("Test reason", response.reason);
    }

    @Test
    void testCountryDataCoverage() {
        CountryData data = new CountryData(1000000L, List.of("es"));
        assertEquals(1000000L, data.population);
        assertEquals(List.of("es"), data.languages);
    }

    @Test
    void testHolidayCoverage() {
        LocalDate now = LocalDate.now();
        Holiday holiday = new Holiday(now);
        assertEquals(now, holiday.date);
    }

    @Test
    void testWeatherDataCoverage() {
        WeatherData data = new WeatherData(25.5, 50);
        assertEquals(25.5, data.temperatureMax);
        assertEquals(50, data.precipitationProbability);
    }

    @Test
    void testRiskLevelCoverage() {
        assertNotNull(RiskLevel.valueOf("SAFE"));
        assertNotNull(RiskLevel.valueOf("MEDIUM_RISK"));
        assertNotNull(RiskLevel.valueOf("HIGH_RISK"));
        assertEquals(3, RiskLevel.values().length);
    }
}

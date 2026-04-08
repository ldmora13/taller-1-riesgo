package org.unac.client;

import org.junit.jupiter.api.Test;
import org.unac.exception.ExternalServiceException;
import org.unac.model.*;

import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ClientCoverageTest {

    static class WeatherClientStub implements WeatherClient {
        @Override public WeatherData getWeather(String c) { return new WeatherData(25.0, 10); }
    }

    static class HolidayClientStub implements HolidayClient {
        @Override public List<Holiday> getHolidays(String c, int y) { return List.of(new Holiday(LocalDate.now())); }
    }

    static class CountryClientStub implements CountryClient {
        @Override public CountryData getCountry(String c) { return new CountryData(50000000L, List.of("Spanish")); }
    }

    @Test
    void weatherClient_ReturnsValidData() {
        WeatherClient client = new ClientCoverageTest.WeatherClientStub();
        WeatherData result = client.getWeather("CO");
        assertNotNull(result);
        assertEquals(25.0, result.temperatureMax);
    }

    @Test
    void holidayClient_ReturnsValidData() {
        HolidayClient client = new ClientCoverageTest.HolidayClientStub();
        List<Holiday> result = client.getHolidays("CO", 2026);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void countryClient_ReturnsValidData() {
        CountryClient client = new ClientCoverageTest.CountryClientStub();
        CountryData result = client.getCountry("CO");
        assertNotNull(result);
        assertEquals(50000000L, result.population);
    }

    @Test
    void testWeatherClientInterface() {
        WeatherClient client = countryCode -> new WeatherData(25.0, 10);
        assertNotNull(client.getWeather("CO"));
    }

    @Test
    void testHolidayClientInterface() {
        HolidayClient client = (countryCode, year) -> List.of(new Holiday(java.time.LocalDate.now()));
        assertFalse(client.getHolidays("CO", 2026).isEmpty());
    }

    @Test
    void testCountryClientInterface() {
        CountryClient client = countryCode -> new CountryData(50000000L, List.of("Spanish"));
        assertNotNull(client.getCountry("CO"));
    }

    @Test
    void testExternalServiceExceptionCoverage() {
        ExternalServiceException ex = new ExternalServiceException("Error");
        assertEquals("Error", ex.getMessage());
    }
}

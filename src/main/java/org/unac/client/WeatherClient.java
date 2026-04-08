package org.unac.client;

import org.unac.model.WeatherData;

public interface WeatherClient {
    WeatherData getWeather(String countryCode);
}

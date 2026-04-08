package org.unac.client;

import org.unac.model.CountryData;

public interface CountryClient {
    CountryData getCountry(String countryCode);
}

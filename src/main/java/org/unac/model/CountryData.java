package org.unac.model;

import java.util.List;

public class CountryData {
    public long population;
    public List<String> languages;

    public CountryData(long population, List<String> languages) {
        this.population = population;
        this.languages = languages;
    }
}

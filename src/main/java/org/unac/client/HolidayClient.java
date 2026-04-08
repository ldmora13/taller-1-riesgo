package org.unac.client;

import org.unac.model.Holiday;

import java.util.List;

public interface HolidayClient {
    List<Holiday> getHolidays(String countryCode, int year);
}

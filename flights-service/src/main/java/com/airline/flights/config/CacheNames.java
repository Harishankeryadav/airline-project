package com.airline.flights.config;

public final class CacheNames {
    public static final String AIRPORTS = "airports";            // airport lists (all / by city)
    public static final String AIRPORT_SEARCH = "airport-search"; // GET /airports/search
    public static final String FLIGHTS = "flights";               // flight search results
    public static final String FLIGHT = "flight";                 // single flight by id

    private CacheNames() {
    }
}

package com.airline.flights.service;

import com.airline.flights.config.CacheNames;
import com.airline.flights.dto.AirportRequest;
import com.airline.flights.dto.AirportResponse;
import com.airline.flights.dto.AirportUpdateRequest;
import com.airline.flights.entity.Airport;
import com.airline.flights.entity.City;
import com.airline.flights.exception.BadRequestException;
import com.airline.flights.exception.DuplicateResourceException;
import com.airline.flights.exception.ResourceNotFoundException;
import com.airline.flights.mapper.Mappers;
import com.airline.flights.repository.AirportRepository;
import com.airline.flights.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AirportService {

    public static final int MAX_SEARCH_RESULTS = 50;
    private static final char LIKE_ESCAPE = '!';

    private final AirportRepository airportRepository;
    private final CityRepository cityRepository;

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.AIRPORTS, CacheNames.AIRPORT_SEARCH}, allEntries = true)
    public AirportResponse create(AirportRequest request) {
        City city = findCity(request.cityId());
        String code = normalizeCode(request.code());
        if (code != null && airportRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Airport code already exists: " + code);
        }
        Airport airport = new Airport();
        airport.setName(request.name().trim());
        airport.setCode(code);
        airport.setAddress(request.address());
        airport.setCity(city);
        return Mappers.toAirportResponse(airportRepository.save(airport));
    }

    @Transactional(readOnly = true)
    public AirportResponse get(Long id) {
        return Mappers.toAirportResponse(findOrThrow(id));
    }

    /** All airports, or only those in one city. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AIRPORTS, key = "#cityId == null ? 'all' : 'city:' + #cityId")
    public List<AirportResponse> getAll(Long cityId) {
        List<Airport> airports = cityId == null
                ? airportRepository.findAllWithCity()
                : airportRepository.findByCityIdWithCity(cityId);
        return Mappers.mapAll(airports, Mappers::toAirportResponse);
    }

    /**
     * Find airports by airport name or city name (partial, case-insensitive).
     * Results whose airport/city name STARTS with the text come first.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.AIRPORT_SEARCH,
            key = "T(java.util.Objects).toString(#q, '').trim().toLowerCase() + ':' + #limit")
    public List<AirportResponse> search(String q, int limit) {
        if (q == null || q.isBlank()) {
            throw new BadRequestException("Search text 'q' must not be blank");
        }
        String escaped = escapeLike(q.trim().toLowerCase());
        int size = Math.min(Math.max(limit, 1), MAX_SEARCH_RESULTS);
        List<Airport> matches = airportRepository.search("%" + escaped + "%", escaped + "%", PageRequest.of(0, size));
        return Mappers.mapAll(matches, Mappers::toAirportResponse);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.AIRPORTS, CacheNames.AIRPORT_SEARCH, CacheNames.FLIGHTS, CacheNames.FLIGHT},
            allEntries = true)
    public AirportResponse update(Long id, AirportUpdateRequest request) {
        Airport airport = findOrThrow(id);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("name must not be blank");
            }
            airport.setName(request.name().trim());
        }
        if (request.code() != null) {
            String code = normalizeCode(request.code());
            if (!code.equalsIgnoreCase(airport.getCode()) && airportRepository.existsByCodeIgnoreCase(code)) {
                throw new DuplicateResourceException("Airport code already exists: " + code);
            }
            airport.setCode(code);
        }
        if (request.address() != null) {
            airport.setAddress(request.address());
        }
        if (request.cityId() != null) {
            airport.setCity(findCity(request.cityId()));
        }
        return Mappers.toAirportResponse(airport);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.AIRPORTS, CacheNames.AIRPORT_SEARCH}, allEntries = true)
    public void delete(Long id) {
        airportRepository.delete(findOrThrow(id));
        airportRepository.flush(); // flights still using this airport -> FK violation -> 409
    }

    private Airport findOrThrow(Long id) {
        return airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found with id " + id));
    }

    private City findCity(Long cityId) {
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id " + cityId));
    }

    private static String normalizeCode(String code) {
        return (code == null || code.isBlank()) ? null : code.trim().toUpperCase();
    }

    /** Neutralises LIKE wildcards typed by the user so '%' and '_' are matched literally. */
    private static String escapeLike(String text) {
        return text.replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}

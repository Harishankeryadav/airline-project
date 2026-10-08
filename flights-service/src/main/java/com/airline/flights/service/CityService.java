package com.airline.flights.service;

import com.airline.flights.config.CacheNames;
import com.airline.flights.dto.CityRequest;
import com.airline.flights.dto.CityResponse;
import com.airline.flights.entity.City;
import com.airline.flights.exception.DuplicateResourceException;
import com.airline.flights.exception.ResourceNotFoundException;
import com.airline.flights.mapper.Mappers;
import com.airline.flights.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;

    @Transactional
    public CityResponse create(CityRequest request) {
        String name = request.name().trim();
        if (cityRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("City already exists: " + name);
        }
        return Mappers.toCityResponse(cityRepository.save(new City(name)));
    }

    @Transactional(readOnly = true)
    public CityResponse get(Long id) {
        return Mappers.toCityResponse(findOrThrow(id));
    }

    /** @param namePrefix optional - only cities whose name starts with this text (case-insensitive) */
    @Transactional(readOnly = true)
    public List<CityResponse> getAll(String namePrefix) {
        Sort sort = Sort.by("name");
        List<City> cities = (namePrefix == null || namePrefix.isBlank())
                ? cityRepository.findAll(sort)
                : cityRepository.findByNameStartingWithIgnoreCase(namePrefix.trim(), sort);
        return Mappers.mapAll(cities, Mappers::toCityResponse);
    }

    // A city name is embedded in cached airport and flight responses, so renaming evicts those caches too.
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.AIRPORTS, CacheNames.AIRPORT_SEARCH, CacheNames.FLIGHTS, CacheNames.FLIGHT},
            allEntries = true)
    public CityResponse update(Long id, CityRequest request) {
        City city = findOrThrow(id);
        String name = request.name().trim();
        if (!city.getName().equalsIgnoreCase(name) && cityRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("City already exists: " + name);
        }
        city.setName(name);
        return Mappers.toCityResponse(city);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.AIRPORTS, CacheNames.AIRPORT_SEARCH}, allEntries = true)
    public void delete(Long id) {
        City city = findOrThrow(id);
        cityRepository.delete(city);
        cityRepository.flush(); // surface FK violations (city still has airports) here -> 409 via the exception handler
    }

    private City findOrThrow(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id " + id));
    }
}

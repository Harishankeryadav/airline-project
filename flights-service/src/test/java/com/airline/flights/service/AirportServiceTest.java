package com.airline.flights.service;

import com.airline.flights.entity.Airport;
import com.airline.flights.entity.City;
import com.airline.flights.dto.AirportResponse;
import com.airline.flights.exception.BadRequestException;
import com.airline.flights.repository.AirportRepository;
import com.airline.flights.repository.CityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AirportServiceTest {

    @Mock
    private AirportRepository airportRepository;
    @Mock
    private CityRepository cityRepository;
    @InjectMocks
    private AirportService airportService;

    @Test
    void searchRejectsBlankQuery() {
        assertThrows(BadRequestException.class, () -> airportService.search("   ", 10));
        assertThrows(BadRequestException.class, () -> airportService.search(null, 10));
    }

    @Test
    void searchLowercasesTrimsAndEscapesLikeWildcards() {
        when(airportRepository.search(anyString(), anyString(), any(Pageable.class))).thenReturn(List.of());

        airportService.search("  100%_X ", 10);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        verify(airportRepository).search(pattern.capture(), prefix.capture(), any(Pageable.class));
        assertEquals("%100!%!_x%", pattern.getValue());
        assertEquals("100!%!_x%", prefix.getValue());
    }

    @Test
    void searchClampsLimitToMaximum() {
        when(airportRepository.search(anyString(), anyString(), any(Pageable.class))).thenReturn(List.of());

        airportService.search("blr", 10_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(airportRepository).search(anyString(), anyString(), pageable.capture());
        assertEquals(AirportService.MAX_SEARCH_RESULTS, pageable.getValue().getPageSize());
    }

    @Test
    void searchMapsAirportAndCityNames() {
        City city = new City("Bengaluru");
        city.setId(1L);
        Airport airport = new Airport();
        airport.setId(5L);
        airport.setName("Kempegowda International Airport");
        airport.setCode("BLR");
        airport.setCity(city);
        when(airportRepository.search(anyString(), anyString(), any(Pageable.class))).thenReturn(List.of(airport));

        List<AirportResponse> results = airportService.search("beng", 5);

        assertEquals(1, results.size());
        assertEquals("Bengaluru", results.get(0).cityName());
        assertEquals("BLR", results.get(0).code());
    }
}

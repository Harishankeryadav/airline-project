package com.airline.flights.service;

import com.airline.flights.dto.AirplaneRequest;
import com.airline.flights.dto.AirplaneResponse;
import com.airline.flights.entity.Airplane;
import com.airline.flights.exception.ResourceNotFoundException;
import com.airline.flights.mapper.Mappers;
import com.airline.flights.repository.AirplaneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AirplaneService {

    private final AirplaneRepository airplaneRepository;

    @Transactional
    public AirplaneResponse create(AirplaneRequest request) {
        Airplane airplane = new Airplane();
        airplane.setModelNumber(request.modelNumber().trim());
        if (request.capacity() != null) {
            airplane.setCapacity(request.capacity());
        }
        return Mappers.toAirplaneResponse(airplaneRepository.save(airplane));
    }

    @Transactional(readOnly = true)
    public AirplaneResponse get(Long id) {
        return Mappers.toAirplaneResponse(airplaneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airplane not found with id " + id)));
    }

    @Transactional(readOnly = true)
    public List<AirplaneResponse> getAll() {
        return Mappers.mapAll(airplaneRepository.findAll(Sort.by("modelNumber")), Mappers::toAirplaneResponse);
    }
}

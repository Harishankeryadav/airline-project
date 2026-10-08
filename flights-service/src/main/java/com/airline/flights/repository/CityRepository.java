package com.airline.flights.repository;

import com.airline.flights.entity.City;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<City> findByNameStartingWithIgnoreCase(String prefix, Sort sort);
}

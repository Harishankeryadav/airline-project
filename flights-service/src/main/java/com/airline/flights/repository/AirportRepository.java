package com.airline.flights.repository;

import com.airline.flights.entity.Airport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AirportRepository extends JpaRepository<Airport, Long> {

    boolean existsByCodeIgnoreCase(String code);

    @Query("select a from Airport a join fetch a.city order by a.name")
    List<Airport> findAllWithCity();

    @Query("select a from Airport a join fetch a.city c where c.id = :cityId order by a.name")
    List<Airport> findByCityIdWithCity(@Param("cityId") Long cityId);

    /**
     * Finds airports whose own name OR city name matches {@code pattern} (a pre-escaped, lower-cased LIKE pattern;
     * the escape character is '!'). Matches that START with the term ({@code prefix}) are ranked first.
     */
    @Query("""
            select a from Airport a join fetch a.city c
            where lower(a.name) like :pattern escape '!'
               or lower(c.name) like :pattern escape '!'
            order by case when lower(a.name) like :prefix escape '!'
                            or lower(c.name) like :prefix escape '!' then 0 else 1 end,
                     c.name, a.name
            """)
    List<Airport> search(@Param("pattern") String pattern,
                         @Param("prefix") String prefix,
                         Pageable pageable);
}

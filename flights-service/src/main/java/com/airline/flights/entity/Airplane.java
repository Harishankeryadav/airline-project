package com.airline.flights.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "airplanes")
@Getter
@Setter
@NoArgsConstructor
public class Airplane extends BaseEntity {

    @Column(name = "model_number", nullable = false, length = 100)
    private String modelNumber;

    @Column(nullable = false)
    private int capacity = 200;
}

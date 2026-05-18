package com.shiptracker.repository;

import com.shiptracker.model.Port;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortRepository extends JpaRepository<Port, Long> {
    List<Port> findByCountryIgnoreCase(String country);
    List<Port> findByNameContainingIgnoreCase(String name);
}

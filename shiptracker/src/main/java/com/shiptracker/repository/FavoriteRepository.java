package com.shiptracker.repository;

import com.shiptracker.model.Favorite;
import com.shiptracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserOrderByAddedAtDesc(User user);
    Optional<Favorite> findByUserAndMmsi(User user, String mmsi);
    boolean existsByUserAndMmsi(User user, String mmsi);
    void deleteByUserAndMmsi(User user, String mmsi);

    @Query("SELECT f FROM Favorite f JOIN FETCH f.user WHERE f.mmsi = :mmsi AND f.alertOnDeparture = true")
    List<Favorite> findDepartureAlertsByMmsi(@Param("mmsi") String mmsi);

    @Query("SELECT f FROM Favorite f JOIN FETCH f.user WHERE f.mmsi = :mmsi AND f.alertOnArrival = true")
    List<Favorite> findArrivalAlertsByMmsi(@Param("mmsi") String mmsi);
}

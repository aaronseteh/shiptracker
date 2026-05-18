package com.shiptracker.service;

import com.shiptracker.dto.ShipDTO;
import com.shiptracker.model.Favorite;
import com.shiptracker.model.User;
import com.shiptracker.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public List<Favorite> getUserFavorites(User user) {
        return favoriteRepository.findByUserOrderByAddedAtDesc(user);
    }

    public boolean isFavorite(User user, String mmsi) {
        return favoriteRepository.existsByUserAndMmsi(user, mmsi);
    }

    public Favorite addFavorite(User user, ShipDTO ship, String notes) {
        if (favoriteRepository.existsByUserAndMmsi(user, ship.getMmsi())) {
            return favoriteRepository.findByUserAndMmsi(user, ship.getMmsi()).orElseThrow();
        }
        Favorite fav = new Favorite();
        fav.setUser(user);
        fav.setMmsi(ship.getMmsi());
        fav.setShipName(ship.getName());
        fav.setShipType(ship.getType());
        fav.setShipFlag(ship.getFlag());
        fav.setNotes(notes);
        return favoriteRepository.save(fav);
    }

    @Transactional
    public void removeFavorite(User user, String mmsi) {
        favoriteRepository.deleteByUserAndMmsi(user, mmsi);
    }

    public void updateNotes(Long favoriteId, String notes) {
        favoriteRepository.findById(favoriteId).ifPresent(fav -> {
            fav.setNotes(notes);
            favoriteRepository.save(fav);
        });
    }

    public void updateAlerts(Long favoriteId, boolean alertOnDeparture, boolean alertOnArrival) {
        favoriteRepository.findById(favoriteId).ifPresent(fav -> {
            fav.setAlertOnDeparture(alertOnDeparture);
            fav.setAlertOnArrival(alertOnArrival);
            favoriteRepository.save(fav);
        });
    }
}

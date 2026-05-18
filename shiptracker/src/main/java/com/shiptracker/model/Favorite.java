package com.shiptracker.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorites",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "mmsi"}))
@Data
@NoArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String mmsi;

    private String shipName;
    private String shipType;
    private String shipFlag;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    private Boolean alertOnDeparture = false;  // avisa cuando pasa de "Atracado" → "En navegación"
    private Boolean alertOnArrival   = false;  // avisa cuando pasa de "En navegación" → "Atracado"
}

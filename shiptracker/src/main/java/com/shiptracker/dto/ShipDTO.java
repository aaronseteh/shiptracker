package com.shiptracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipDTO {
    private String mmsi;
    private String imo;
    private String name;
    private String type;
    private String flag;
    private String callSign;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double course;
    private String status;
    private String destination;
    private String portOfOrigin;
    private Double length;
    private Double width;
    private Double draught;
    private String lastUpdate;
    private boolean favorite;
}

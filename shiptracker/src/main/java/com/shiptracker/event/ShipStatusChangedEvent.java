package com.shiptracker.event;

public class ShipStatusChangedEvent {

    private final String mmsi;
    private final String shipName;
    private final String previousStatus;
    private final String newStatus;
    private final String destination;

    public ShipStatusChangedEvent(String mmsi, String shipName,
                                   String previousStatus, String newStatus,
                                   String destination) {
        this.mmsi           = mmsi;
        this.shipName       = shipName;
        this.previousStatus = previousStatus;
        this.newStatus      = newStatus;
        this.destination    = destination;
    }

    public String getMmsi()           { return mmsi; }
    public String getShipName()       { return shipName; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus()      { return newStatus; }
    public String getDestination()    { return destination; }
}

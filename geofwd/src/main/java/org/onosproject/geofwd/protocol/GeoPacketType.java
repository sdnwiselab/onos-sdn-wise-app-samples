package org.onosproject.geofwd.protocol;

import org.onosproject.net.sensorpacket.SensorPacketTypeRegistry.SensorPacketType;

import static org.onosproject.net.sensorpacket.SensorPacketTypeRegistry.getPacketType;

/**
 * Created by aca on 12/3/15.
 */
public enum GeoPacketType {
    GEO_COORDINATES(getPacketType("GEO_COORDINATES")),
    GEO_DATA(getPacketType("GEO_DATA")),
    GEO_MULTICAST_DATA(getPacketType("GEO_MULTICAST_DATA"));

    private SensorPacketType sensorPacketType;

    GeoPacketType(SensorPacketType sensorPacketType) {
        this.sensorPacketType = sensorPacketType;
    }

    public SensorPacketType getSensorPacketType() {
        return this.sensorPacketType;
    }
}

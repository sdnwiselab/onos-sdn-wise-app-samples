package org.onosproject.gem;

import org.onosproject.net.sensorpacket.SensorPacketTypeRegistry.SensorPacketType;

import static org.onosproject.net.sensorpacket.SensorPacketTypeRegistry.getPacketType;

/**
 * Created by aca on 9/16/15.
 */
public enum GemPacketType {
    MULTICAST_CTRL_DATA(getPacketType(13, "MULTICAST_CTRL_DATA")),
    MULTICAST_DATA(getPacketType(10, "MULTICAST_DATA"));

    private SensorPacketType sensorPacketType;

    GemPacketType(SensorPacketType packetType) {
        this.sensorPacketType = packetType;
    }

    public SensorPacketType getSensorPacketType() {
        return sensorPacketType;
    }
}

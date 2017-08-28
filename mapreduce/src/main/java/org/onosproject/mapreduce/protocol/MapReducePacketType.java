package org.onosproject.mapreduce.protocol;

import org.onosproject.net.sensorpacket.SensorPacketTypeRegistry.SensorPacketType;

import static org.onosproject.net.sensorpacket.SensorPacketTypeRegistry.getPacketType;

/**
 * Created by aca on 9/16/15.
 */
public enum MapReducePacketType {
    MAP_DATA(getPacketType("MAP_DATA")),
    REDUCE_DATA(getPacketType("REDUCE_DATA")),
    MAP_FUNCTION_TRIGGER(getPacketType("MAP_FUNCTION_TRIGGER"));

    private SensorPacketType sensorPacketType;

    MapReducePacketType(SensorPacketType type) {
        this.sensorPacketType = type;
    }

    public SensorPacketType getSensorPacketType() {
        return sensorPacketType;
    }
}

package org.onosproject.mapreduce.protocol;

import com.google.common.base.MoreObjects;
import org.onosproject.mapreduce.profile.SensorType;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.onosproject.mapreduce.protocol.MapReducePacketType.MAP_DATA;

/**
 * Created by aca on 9/17/15.
 */
public final class MapFunctionTriggerPacket implements MapReducePacket {
    private int packetType;
    private int[] keys;
    private int keyCount = 0;

    public MapFunctionTriggerPacket(List<SensorType> sensorTypes) {
        this.packetType = MAP_DATA.getSensorPacketType().originalId();
        this.keys = new int[sensorTypes.size()];
        sensorTypes.forEach(this::setKeyFromSensorType);
    }

    public MapFunctionTriggerPacket(int packetType, int[] keys) {
        this.packetType = packetType;
        this.keys = keys;
    }

    private void setKeyFromSensorType(SensorType sensorType) {
        keys[keyCount++] = sensorType.getValue();
    }

    public int[] keys() {
        return keys;
    }

    public int packetType() {
        return packetType;
    }

    @Override
    public byte[] serialize() {
        byte[] rawData = new byte[1 + keys.length];

        rawData[0] = (byte) packetType;
        int i = 1;
        for (int key : keys) {
            rawData[i++] = (byte) key;
        }
        return rawData;
    }

    public static MapFunctionTriggerPacket fromRawData(byte[] rawData) {
        int packetType = ByteBuffer.allocate(2).put(new byte[] {rawData[0], rawData[1]}).getInt();
        int keysLength = (rawData.length - 2) / 2;
        int[] keys = new int[keysLength];
        int off = 2;
        for (int i = 0; i < keysLength; i++) {
            keys[i] = ByteBuffer.allocate(2).put(new byte[]{rawData[off++], rawData[off++]}).getInt();
        }

        return new MapFunctionTriggerPacket(packetType, keys);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MapFunctionTriggerPacket.class)
                .add("packetType", packetType)
                .add("keys", Arrays.toString(keys))
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MapFunctionTriggerPacket)) {
            return false;
        }

        MapFunctionTriggerPacket that = (MapFunctionTriggerPacket) o;

        if (packetType != that.packetType) {
            return false;
        }
        return Arrays.equals(keys, that.keys);
    }

    @Override
    public int hashCode() {
        int result = packetType;
        result = 31 * result + Arrays.hashCode(keys);
        return result;
    }
}

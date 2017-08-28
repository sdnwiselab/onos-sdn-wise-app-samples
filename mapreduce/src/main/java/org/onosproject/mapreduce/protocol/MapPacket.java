package org.onosproject.mapreduce.protocol;

import com.google.common.base.MoreObjects;

import java.nio.ByteBuffer;

/**
 * Created by aca on 9/16/15.
 */
public final class MapPacket implements MapReducePacket {
    public static final int KEY_BYTE_POS = 10;
    public static final int VALUE_BYTE_POS = 11;
    public static final int LAST_PAYLOAD_BYTE = 14;
    private byte key;
    private int value;

    public MapPacket(byte key, int value) {
        this.key = key;
        this.value = value;
    }

    public byte getKey() {
        return key;
    }

    public int getValue() {
        return value;
    }

    public static MapPacket fromRawData(byte[] rawData) {
        byte key = rawData[0];
        byte[] val = new byte[4];
        System.arraycopy(rawData, 1, val, 0, 4);

        ByteBuffer byteBuffer = ByteBuffer.wrap(val);
        int value = byteBuffer.getInt();

        return new MapPacket(key, value);
    }

    public byte[] serialize() {
        byte[] serializedData = new byte[6];
        serializedData[0] = key;
        serializedData[1] = 0;
        serializedData[2] = 0;
        serializedData[3] = 0;
        serializedData[4] = (byte) value;
        serializedData[5] = 0;

        return serializedData;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MapPacket.class)
                .add("key", key)
                .add("value", value)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MapPacket)) {
            return false;
        }

        MapPacket mapPacket = (MapPacket) o;

        if (key != mapPacket.key) {
            return false;
        }
        return value == mapPacket.value;

    }

    @Override
    public int hashCode() {
        int result = (int) key;
        result = 31 * result + value;
        return result;
    }
}

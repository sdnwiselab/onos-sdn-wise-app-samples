package org.onosproject.mapreduce.protocol;

import java.nio.ByteBuffer;

/**
 * Created by aca on 9/16/15.
 */
public final class ReducePacket implements MapReducePacket {
    public static final byte KEY_POS = 10;
    private byte key;
    private double value;

    public ReducePacket(byte key, double value) {
        this.key = key;
        this.value = value;
    }

    public byte getKey() {
        return key;
    }

    public double getValue() {
        return value;
    }

    public static ReducePacket fromRawData(byte[] rawData) {
        byte key = rawData[0];
        byte[] valueArr = new byte[Double.BYTES];
        System.arraycopy(rawData, 1, valueArr, 0, 8);
        double val = ByteBuffer.wrap(valueArr).getDouble();

        return new ReducePacket(key, val);
    }

    public byte[] serialize() {
        int size = 9;
        byte[] payload = new byte[size];

        payload[0] = key;
        byte[] valueArr = ByteBuffer.allocate(Double.BYTES).putDouble(value).array();
        System.arraycopy(valueArr, 0, payload, 1, 8);

        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReducePacket)) {
            return false;
        }

        ReducePacket that = (ReducePacket) o;

        if (key != that.key) {
            return false;
        }
        return Double.compare(that.value, value) == 0;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) key;
        temp = Double.doubleToLongBits(value);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}

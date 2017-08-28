package org.onosproject.mapreduce.profile;

/**
 * Created by aca on 9/16/15.
 */
public enum SensorType {
    TEMPERATURE((byte) 1),
    PRESSURE((byte) 2),
    LIGHT((byte) 3),
    NOISE((byte) 4);

    byte value;
    SensorType(byte value) {
        this.value = value;
    }

    public static SensorType getSensorType(byte value) {
        for (SensorType sensorType : values()) {
            if (sensorType.value == value) {
                return sensorType;
            }
        }

        return null;
    }

    public byte getValue() {
        return value;
    }
}

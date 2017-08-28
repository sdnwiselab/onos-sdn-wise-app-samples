package org.onosproject.mapreduce.protocol;

/**
 * Created by aca on 9/17/15.
 */
public enum MapReduceFunctionType {
    PROTO_ENCAPSULATION_FUNCTION((byte) 1),
    MAP_FUNCTION((byte) 2),
    REDUCE_FUNCTION((byte) 3);

    private byte id;
    MapReduceFunctionType(byte id) {
        this.id = id;
    }

    public byte functionId() {
        return id;
    }
}

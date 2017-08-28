package org.onosproject.mapreduce.protocol;

/**
 * Created by aca on 9/17/15.
 */
public interface MapReducePacket {
    byte[] serialize();

    static MapReducePacket fromRawData(byte[] rawData) {
        return null;
    }
}

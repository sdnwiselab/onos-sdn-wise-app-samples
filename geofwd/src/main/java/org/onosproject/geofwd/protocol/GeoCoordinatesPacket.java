package org.onosproject.geofwd.protocol;

import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.sensor.SensorNodeAddress;

import static org.onosproject.geofwd.protocol.GeoPacketType.GEO_COORDINATES;

/**
 * Created by aca on 12/4/15.
 */
public class GeoCoordinatesPacket implements GeoPacket {
    private SensorNodeAddress sensorNodeAddress;
    private int xCoord;
    private int yCoord;

    public GeoCoordinatesPacket(SensorNodeAddress sensorNodeAddress, int xCoord, int yCoord) {
        this.sensorNodeAddress = sensorNodeAddress;
        this.xCoord = xCoord;
        this.yCoord = yCoord;
    }

    public static GeoCoordinatesPacket fromInboundPacket(SensorNodeAddress curNodeAddress,
                                                         InboundPacket inboundPacket) {
        byte[] payload = inboundPacket.parsed().serialize();
        SensorNodeAddress sensorNodeAddress = new SensorNodeAddress(curNodeAddress.getNetId(),
                new byte[] {payload[0], payload[1]});
        int xCoord = ((payload[2] & 0xFF) * 256) + (payload[3] & 0xFF);
        int yCoord = ((payload[4] & 0xFF) * 256) + (payload[5] & 0xFF);

        return new GeoCoordinatesPacket(sensorNodeAddress, xCoord, yCoord);
    }

    public SensorNodeAddress getSensorNodeAddress() {
        return sensorNodeAddress;
    }

    public int getxCoord() {
        return xCoord;
    }

    public int getyCoord() {
        return yCoord;
    }

    @Override
    public GeoPacketType getPacketType() {
        return GEO_COORDINATES;
    }
}

package org.onosproject.geofwd.protocol;

import org.onosproject.net.packet.InboundPacket;

import static org.onosproject.geofwd.protocol.GeoPacketType.GEO_DATA;

/**
 * Created by aca on 12/4/15.
 */
public class GeoDataPacket implements GeoPacket {
    private int dstXCoord;
    private int dstYCoord;

    public GeoDataPacket(int dstXCoord, int dstYCoord) {
        this.dstXCoord = dstXCoord;
        this.dstYCoord = dstYCoord;
    }

    public static GeoDataPacket fromInboundPacket(InboundPacket inboundPacket) {
        byte[] payload = inboundPacket.parsed().serialize();
        int xCoord = ((payload[0] & 0xFF) * 256) + (payload[1] & 0xFF);
        int yCoord = ((payload[2] & 0xFF) * 256) + (payload[3] & 0xFF);

        return new GeoDataPacket(xCoord, yCoord);
    }

    public int getDstXCoord() {
        return dstXCoord;
    }

    public int getDstYCoord() {
        return dstYCoord;
    }

    @Override
    public GeoPacketType getPacketType() {
        return GEO_DATA;
    }
}

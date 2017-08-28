package org.onosproject.geofwd.protocol;

import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.sensor.SensorNodeAddress;

import static org.onosproject.geofwd.protocol.GeoPacketType.GEO_MULTICAST_DATA;

/**
 * Created by aca on 12/4/15.
 */
public class GeoMulticastDataPacket extends GeoDataPacket implements GeoPacket {
    private int groupId;
    private SensorNodeAddress prevMulNodeAddress;
    private SensorNodeAddress curMulNodeAddress;

    public GeoMulticastDataPacket(int dstXCoord, int dstYCoord, int groupId,
                                  SensorNodeAddress prevMulNodeAddress, SensorNodeAddress curMulNodeAddress) {
        super(dstXCoord, dstYCoord);
        this.groupId = groupId;
        this.prevMulNodeAddress = prevMulNodeAddress;
        this.curMulNodeAddress = curMulNodeAddress;
    }

    public GeoMulticastDataPacket fromInboundPacket(SensorNodeAddress curNodeAddress, InboundPacket inboundPacket) {
        byte[] payload = inboundPacket.parsed().serialize();

        int xCoord = ((payload[0] & 0xFF) * 256) + (payload[1] & 0xFF);
        int yCoord = ((payload[2] & 0xFF) * 256) + (payload[3] & 0xFF);

        byte netId = curNodeAddress.getNetId();
        int groupId = ((payload[4] & 0xFF) * 256) + (payload[5] & 0xFF);

        SensorNodeAddress prevMulNodeAddress = new SensorNodeAddress(netId, new byte[] {payload[8], payload[9]});
        SensorNodeAddress curMulNodeAddress = new SensorNodeAddress(netId, new byte[] {payload[9], payload[10]});

        GeoMulticastDataPacket geoMulticastDataPacket = new GeoMulticastDataPacket(
                xCoord, yCoord, groupId, prevMulNodeAddress, curMulNodeAddress);

        return geoMulticastDataPacket;
    }

    public int getGroupId() {
        return groupId;
    }

    public SensorNodeAddress getPrevMulNodeAddress() {
        return prevMulNodeAddress;
    }

    public SensorNodeAddress getCurMulNodeAddress() {
        return curMulNodeAddress;
    }

    @Override
    public GeoPacketType getPacketType() {
        return GEO_MULTICAST_DATA;
    }
}

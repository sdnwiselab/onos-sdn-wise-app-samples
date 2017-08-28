package org.onosproject.gem.topology;

import org.onosproject.net.DeviceId;
import org.onosproject.net.SensorNodeId;
import org.onosproject.net.SensorNodeLocalization;

/**
 * Created by aca on 4/28/15.
 */
public class GeoNode implements SensorNodeLocalization {
    private DeviceId deviceId;
    private double xCoord;
    private double yCoord;

    private boolean isSteinerNode;
    private boolean isMulticastGroupNode;

    public GeoNode(DeviceId deviceId, double xCoord, double yCoord) {
        this.deviceId = deviceId;
        this.xCoord = xCoord;
        this.yCoord = yCoord;
        this.isSteinerNode = false;
        this.isMulticastGroupNode = false;
    }

    public GeoNode(double xCoord, double yCoord) {
        this.xCoord = xCoord;
        this.yCoord = yCoord;
        this.isSteinerNode = false;
    }

    public DeviceId getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(DeviceId deviceId) {
        this.deviceId = deviceId;
    }

    public double getxCoord() {
        return xCoord;
    }

    public void setxCoord(double xCoord) {
        this.xCoord = xCoord;
    }

    public double getyCoord() {
        return yCoord;
    }

    public void setyCoord(double yCoord) {
        this.yCoord = yCoord;
    }

    public void setSteiner() {
        this.isSteinerNode = true;
    }

    public boolean isSteiner() {
        return this.isSteinerNode;
    }

    public void setMulticastGroupMember() {
        this.isMulticastGroupNode = true;
    }

    public boolean isMulticastGroupMember() {
        return this.isMulticastGroupNode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoNode)) {
            return false;
        }

        GeoNode geoNode = (GeoNode) o;

        if (Double.compare(geoNode.xCoord, xCoord) != 0) {
            return false;
        }
        return Double.compare(geoNode.yCoord, yCoord) == 0;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(xCoord);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(yCoord);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public double[] xyzCoordinates(SensorNodeId sensorNodeId) {
        return new double[0];
    }
}

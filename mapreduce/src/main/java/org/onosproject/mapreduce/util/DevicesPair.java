package org.onosproject.mapreduce.util;

import org.onosproject.net.DeviceId;

/**
 * Created by aca on 10/5/15.
 */
public class DevicesPair {
    private DeviceId dev1;
    private DeviceId dev2;

    public DevicesPair(DeviceId dev1, DeviceId dev2) {
        this.dev1 = dev1;
        this.dev2 = dev2;
    }

    public DeviceId dev1() {
        return dev1;
    }

    public DeviceId dev2() {
        return dev2;
    }

    @Override
    public String toString() {
        return dev1.toString() + "--" + dev2.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DevicesPair)) {
            return false;
        }

        DevicesPair that = (DevicesPair) o;

        if (((dev1.equals(that.dev1)) && (dev2.equals(that.dev2))) ||
                ((dev1.equals(that.dev2)) && (dev2.equals(that.dev1)))) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = dev1.hashCode() + dev2.hashCode();
        return result;
    }
}

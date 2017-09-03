package org.onosproject.mapreduce.function;

import com.github.sdnwiselab.sdnwise.flowtable.FlowTableEntry;
import com.github.sdnwiselab.sdnwise.function.FunctionInterface;
import com.github.sdnwiselab.sdnwise.packet.NetworkPacket;
import com.github.sdnwiselab.sdnwise.util.Neighbor;
import com.github.sdnwiselab.sdnwise.util.NodeAddress;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by aca on 9/16/15.
 */
public final class ToUdp implements FunctionInterface {
    @Override
    public void function(HashMap<String, Object> adcRegister,
                         List<FlowTableEntry> flowTable,
                         Set<Neighbor> neighborTable,
                         ArrayList<Integer> statusRegister,
                         List<NodeAddress> acceptedId,
                         ArrayBlockingQueue<NetworkPacket> flowTableQueue,
                         ArrayBlockingQueue<NetworkPacket> txQueue,
                         byte[] bytes,
                         NetworkPacket np) {

        try {

            byte[] addr = new byte[4];
            addr[0] = bytes[0];
            addr[1] = bytes[1];
            addr[2] = bytes[2];
            addr[3] = bytes[3];

            InetAddress dstAddress = InetAddress.getByAddress(addr);
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket pck = new DatagramPacket(np.toByteArray(), np.getLen(),
                    new InetSocketAddress(dstAddress, ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff)));
            socket.send(pck);
            socket.close();
            System.out.println("Sent packet to " + pck.getAddress() + ":" + pck.getPort());


//            // Pick the network interface to reach the remote address
//            NetworkInterface selectedIface = null;
//            InetAddress dstAddress = InetAddress.getByAddress(addr);
//            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
//            while ((networkInterfaces.hasMoreElements()) && (selectedIface == null)) {
//                NetworkInterface networkInterface = networkInterfaces.nextElement();
//                Logger.getLogger(ToUdp.class.getName()).log(Level.SEVERE,
//                        "Checking Interface " + networkInterface.getDisplayName(),
//                        selectedIface);
//                if (dstAddress.isReachable(networkInterface, 100, 3000)) {
//                    selectedIface = networkInterface;
//                }
//            }
//
//            if (selectedIface != null) {
//                Enumeration<InetAddress> ifaceAddresses = selectedIface.getInetAddresses();
//                InetAddress addressToUse = null;
//                while (ifaceAddresses.hasMoreElements()) {
//                    addressToUse = ifaceAddresses.nextElement();
//                    break;
//                }
//                DatagramSocket socket = new DatagramSocket(new InetSocketAddress(addressToUse, 60000));
//                DatagramPacket pck = new DatagramPacket(np.toByteArray(), np.getLen(),
//                        new InetSocketAddress(dstAddress, arg3));
//                socket.send(pck);
//                socket.close();
//
//                System.out.println("Sent packet to " + pck.getAddress() + ":" + pck.getPort());
//            } else {
//                Logger.getLogger(ToUdp.class.getName()).log(Level.SEVERE, "Could not find appropriate IFace",
//                        selectedIface);
//            }

        } catch (IOException ex) {
            Logger.getLogger(ToUdp.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }

    }
}

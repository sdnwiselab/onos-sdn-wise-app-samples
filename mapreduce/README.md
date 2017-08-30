Integrated Packet Forwarding and MapReduce
====================================
### Integrated Packet Forwarding
This network application leverages ONOS-IoT functionality of providing a unified view of OpenFlow switches and SDN-WISE sensor nodes and finds the optimal paths to forward sensor packets.
Such paths may include heterogeneous network segments.
Despite this heterogeneity, packets are transparently forwarded across either OpenFlow switches or SDN-WISE sensor nodes.

### MapReduce
Integrated packet forwarding has been tested under a MapReduce use case.
This case has been emulated over Mininet.
Each node is assumed to be equipped with some sensors.
Each sensor produces a key-value pair, equivalently to the map phase in MapReduce.
Then, ONOS-IoT chooses the optimal node to become the reducer.
The choice of the reducer is different depending on whether Integrated Packet Forwarding is enabled.
In fact, if it is enabled, several packets go through the OpenFlow switches, hence thereby reducing the WSN traffic.

### Mininet scripts
In order to deploy the MapReduce application, we also provide the scripts that have to be loaded through Mininet.
These scripts create a mixed OpenFlow/SDN-WISE network.
The source code for the scripts can be found [here](https://github.com/sdnwiselab/onos-sdn-wise-app-samples/tree/master/mapreduce/scripts).

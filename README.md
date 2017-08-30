ONOS-IoT Sample network applications
====================================
### Sample Applications
This repository contains sample and testing network applications for the ONOS-IoT network operating system.
These applications demonstrate the use of ONOS-IoT on three different case:
* Integrated packet forwarding across switches and sensor nodes.
This network application leverages ONOS-IoT functionality of providing a unified view of OpenFlow switches and SDN-WISE sensor nodes and finds the optimal paths to forward sensor packets.
Such paths may include heterogeneous network segments.
Despite this heterogeneity, packets are transparently forwarded.
This network application has been used in the context of an in-network MapReduce use case.
Both the network application and the Mininet scripts that can be used to deploy the use case can be found [here](https://github.com/sdnwiselab/onos-sdn-wise-app-samples/tree/master/mapreduce).
* Geographic packet forwarding.
This network application leverages the in-network processing functionality provided by SDN-WISE, in order to perform geographic forwarding.
In this case, all sensor nodes send to ONOS-IoT their neighborhood along with the corresponding RSSI values.
Then, ONOS-IoT calculates their coordinates and sends them to the nodes.
When a node wants to send a packet to an unknown destination, it asks ONOS-IoT to get the coordinates of the destination.
Then, it forwards the packet to its neighbor, which has the smallest Euclidean distance to the destination.
The source code for this network application can be found [here](https://github.com/sdnwiselab/onos-sdn-wise-app-samples/tree/master/geofwd).
* Geographic multicast.
This network application is based on the Geographic forwarding one, however it focuses on the multicast case.
It takes care of group management, whereas it leverages Euclidean Steiner trees to find the optimal path to all the destinations.
The source code for this network application can be found [here](https://github.com/sdnwiselab/onos-sdn-wise-app-samples/tree/master/gem).

Please note that the latter two applications are in experimental state.

### Resources
ONOS-IoT repository: https://github.com/sdnwiselab/onos

SDN-WISE on Contiki: https://github.com/sdnwiselab/sdn-wise-contiki

SDN-WISE emulated node in Java: https://github.com/sdnwiselab/sdn-wise-java

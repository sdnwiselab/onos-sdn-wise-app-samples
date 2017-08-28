#!/usr/bin/python

from sensornet import MiniSensorNet
from mininet.log import setLogLevel
from mininet.cli import CLI
from mininet.node import Host, Node
from mininet.nodelib import NAT
import os
from time import sleep
from threading import Thread

def fixNetworkManager( root, intf ):
		cfile = '/etc/network/interfaces'
		line = '\niface %s inet manual\n' % intf
		config = open( cfile ).read()
		if line not in config:
			print '*** Adding', line.strip(), 'to', cfile
			with open( cfile, 'a' ) as f:
				f.write( line )
			root.cmd( 'service network-manager restart' )

def startNAT( root, inetIntf='eth0', subnet='10.0.1/24' ):
		# Identify the interface connecting to the mininet network
		localIntf = root.defaultIntf()
		print( 'Root local IP: ' + localIntf.IP() )

		# Flush any currently active rules
		root.cmd( 'iptables -F' )
		root.cmd( 'iptables -t nat -F' )

		# Create default entries for unmatched traffic
		root.cmd( 'iptables -P INPUT ACCEPT' )
		root.cmd( 'iptables -P OUTPUT ACCEPT' )
		root.cmd( 'iptables -P FORWARD DROP' )

		# Configure NAT
		root.cmd( 'iptables -I FORWARD -i', localIntf, '-d', subnet, '-j DROP' )
		root.cmd( 'iptables -A FORWARD -i', localIntf, '-s', subnet, '-j ACCEPT' )
		root.cmd( 'iptables -A FORWARD -i', inetIntf, '-d', subnet, '-j ACCEPT' )
		root.cmd( 'iptables -t nat -A POSTROUTING -o ', inetIntf, '-j MASQUERADE' )

		# Instruct the kernel to perform forwarding
		root.cmd( 'sysctl net.ipv4.ip_forward=1' )

#		root.cmd( 'route add -net 192.168.1.0 netmask 255.255.255.0 gw 10.0.2.2' )


def runExperiment( net ):
	
	c0 = net.addController( 'c0', ip='192.168.2.2' )
	
	sensors = {}
	
	networkSize = 23
	
	for i in range( 1, networkSize-1 ):
		sensorName = 'sens' + str( i )
		sensorId = str( i )
		sensors[ i ] = net.addSensor( sensorName, sensorId )
		
	s1 = net.addSwitch( 's1' )
	s2 = net.addSwitch( 's2' )
	s3 = net.addSwitch( 's3' )
	s4 = net.addSwitch( 's4' )
	s5 = net.addSwitch( 's5' )
	s6 = net.addSwitch( 's6' )
	
	net.addLink( s1, s2 )
	net.addLink( s1, s5 )
	net.addLink( s2, s3 )
	net.addLink( s2, s5 )
	net.addLink( s3, s4 )
	net.addLink( s5, s6 )
	
	
	print( 'Starting net' )
	
	# connect sinks to core network
	sensors[ 0 ] = net.addLinkSensorSwitch( 'sens0', '0', s1, '192.168.2.2', '9999', 1000 )
	sensors[ 22 ] = net.addLinkSensorSwitch( 'sens22', '22', s4, '192.168.2.2', '9999', 1001 )
	
	# connect host
	h1 = net.addHost( 'h1', ip = '10.0.1.253/24', defaultRoute = 'via 10.0.1.254' )
	net.addLink( h1, s6 )
	h2 = net.addHost( 'h2', ip = '10.0.1.252/24', defaultRoute = 'via 10.0.1.254' )
	net.addLink( h2, s6 )
	
	# Create a node in root namespace
	root = Node( 'root', inNamespace=False )
	fixNetworkManager( root, 'root-eth0' )
	rootip = '10.0.1.254'
	subnet = '10.0/8'
	link = net.addLink( root, s5 )
	prefixLen = subnet.split( '/' )[ 1 ]
	link.intf1.setIP( rootip, prefixLen )
	
	net.start()
    
	print( 'Starting switches' )
	s1.start( [c0] )
	s2.start( [c0] )
	s3.start( [c0] )
	s4.start( [c0] )
	s5.start( [c0] )
	s6.start( [c0] )
	print( 'Switches started' )
	
	#rootIP = '10.0.1.254'
	#rootFullIP = rootIP + '/24'
	#rsLink = net.addLink( root, s5 )
	#rsLink.intf1.setIP( rootFullIP )
	#fixNetworkManager( root, rsLink.intf1.__str__() )
	
	startNAT( root )
	
	# connect nodes
	net.addLinkSensorSensor( sensors[ 0 ], sensors[ 1 ] )
	net.addLinkSensorSensor( sensors[ 0 ], sensors[ 2 ] )
	net.addLinkSensorSensor( sensors[ 0 ], sensors[ 6 ] )
	net.addLinkSensorSensor( sensors[ 1 ], sensors[ 3 ] )
	net.addLinkSensorSensor( sensors[ 1 ], sensors[ 4 ] )
	net.addLinkSensorSensor( sensors[ 2 ], sensors[ 5 ] )
	net.addLinkSensorSensor( sensors[ 5 ], sensors[ 7 ] )
	net.addLinkSensorSensor( sensors[ 6 ], sensors[ 8 ] )
	net.addLinkSensorSensor( sensors[ 7 ], sensors[ 10 ] )
	net.addLinkSensorSensor( sensors[ 8 ], sensors[ 9 ] )
	net.addLinkSensorSensor( sensors[ 8 ], sensors[ 11 ] )
	net.addLinkSensorSensor( sensors[ 8 ], sensors[ 13 ] )
	net.addLinkSensorSensor( sensors[ 9 ], sensors[ 10 ] )
	net.addLinkSensorSensor( sensors[ 10 ], sensors[ 12 ] )
	net.addLinkSensorSensor( sensors[ 11 ], sensors[ 12 ] )
	net.addLinkSensorSensor( sensors[ 13 ], sensors[ 14 ] )
	net.addLinkSensorSensor( sensors[ 13 ], sensors[ 17 ] )
	net.addLinkSensorSensor( sensors[ 14 ], sensors[ 15 ] )
	net.addLinkSensorSensor( sensors[ 15 ], sensors[ 16 ] )
	net.addLinkSensorSensor( sensors[ 16 ], sensors[ 17 ] )
	net.addLinkSensorSensor( sensors[ 17 ], sensors[ 18 ] )
	net.addLinkSensorSensor( sensors[ 17 ], sensors[ 22 ] )
	net.addLinkSensorSensor( sensors[ 18 ], sensors[ 19 ] )
	net.addLinkSensorSensor( sensors[ 19 ], sensors[ 20 ] )
	net.addLinkSensorSensor( sensors[ 20 ], sensors[ 21 ] )
	net.addLinkSensorSensor( sensors[ 21 ], sensors[ 22 ] )
	
	
	net.startSDNWISE( root )
	
	h1.cmd('java -jar UDPServer.jar 10.0.1.253 6000 &')
	sensors[ 0 ].cmd('ping -c1 10.0.1.3')
	sensors[ 0 ].cmd('ping -c1 10.0.1.253')
	sensors[ 22 ].cmd('ping -c1 10.0.1.253')
	
	CLI( net )
	
	net.stop()
	

if __name__ == '__main__':
	setLogLevel('info')
	net = MiniSensorNet( )
	t = Thread(target=runExperiment, args=[net])
	t.daemon = True
	t.start()
	
	sleep(600)
	
	net.stop()


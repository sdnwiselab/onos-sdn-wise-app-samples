#!/usr/bin/python

from mininet.node import RemoteController, OVSSwitch, Host, Node
from mininet.net import Mininet
from mininet.link import TCLink
from sensornode import SensorNode
from mininet.nodelib import NAT

import math

def fixNetworkManager( root, intf ):
		cfile = '/etc/network/interfaces'
		line = '\niface %s inet manual\n' % intf
		config = open( cfile ).read()
		if line not in config:
			print '*** Adding', line.strip(), 'to', cfile
			with open( cfile, 'a' ) as f:
				f.write( line )
			root.cmd( 'service network-manager restart' )

class MiniSensorNet( Mininet ):
	def __init__( self ):
		super( MiniSensorNet, self ).__init__( controller = RemoteController, switch = OVSSwitch, link = TCLink )
		self.sensorNodes = {}
		self.proxyNodes = {}
		self.proxyCnt = 0
		self.filePrefix = 'Node'
		self.gwLinks = 0
		self.switchGWPort = 1000
		self.udpProxyPort = 50000
		self.tcpProxyPort = 40000
		self.sensorNodePort = 7000
	
	def addSensor( self, name, nodeId, netId = 1, posX = None, posY = None ):
		host = self.addHost( name )
		sensorNode = SensorNode( nodeId, netId, posX, posY )
		self.sensorNodes[ host ] = sensorNode
		return host
		
	def addLinkSensorSwitch( self, name, nodeId, switch, controllerIP, controllerPort, switchPort = None, netId = 1, posX = None, posY = None ):
		# This is the gateway; subnet 1 is reserved for this reason
		self.gwLinks = self.gwLinks + 1
		gwIP = '10.0.1.' + str( self.gwLinks )
		host = self.addHost( name, ip = str( gwIP + '/24' ), defaultRoute = 'via 10.0.1.254' )
		sensorNode = SensorNode( nodeId, netId, posX, posY )
		sensorNode.setGWIP( gwIP )

		if switchPort is not None:
			sensorNode.setSwitchPort( switchPort )
			ssLink = self.addLink( host, switch, port2 = switchPort )
		else:
			sensorNode.setSwitchPort( self.switchGWPort )
			ssLink = self.addLink( host, switch, port2 = self.switchGWPort )
		
#		ssLink.intf1.setIP( gwIP + '/24' )
		self.gwLinks = self.gwLinks + 1
		
		#sensorNode = self.sensorNodes[ sensor ]
		#host.setIP( gwIP )
		
		#self.gwLinks = self.gwLinks + 1
		
		sensorNode.setGW( True, controllerIP, controllerPort, ssLink.intf1, switch )

		self.sensorNodes[ host ] = sensorNode
		return host
	def addLinkSensorSensor( self, src, dst, linkRSSI = None ):
		link = self.addLink( src, dst )
		
		subnetId = self.nextAvailableCommonSubnet( src, dst )
		
		ip1 = '10.0.' + str( subnetId ) + '.1'
		fullIP1 = ip1 + '/24'
		ip2 = '10.0.' + str( subnetId ) + '.2'
		fullIP2 = ip2 + '/24'
		
		link.intf1.setIP( str( fullIP1 ) )
		link.intf2.setIP( str( fullIP2 ) )
		
		#src.cmd( 'ping -c1 ' + ip2 )
		#dst.cmd( 'ping -c1 ' + ip1 )
		
		port1 = self.sensorNodePort + self.sensorNodes.keys().index( src )
		port2 = self.sensorNodePort + self.sensorNodes.keys().index( dst )
		
		srcSensor = self.sensorNodes[ src ]
		dstSensor = self.sensorNodes[ dst ]
		
		srcSensor.setIP( ip1 )
		srcSensor.setPort( port1 )
		dstSensor.setIP( ip2 )
		dstSensor.setPort( port2 )
		
		srcPosX = srcSensor.getX()
		srcPosY = srcSensor.getY()
		dstPosX = dstSensor.getX()
		dstPosY = dstSensor.getY()
		
		pow1 = math.pow( srcPosX - dstPosX, 2 )
		pow2 = math.pow( srcPosY - dstPosY, 2 )
		distance = math.sqrt( pow1 + pow2 )
		linkRssi = 200
		
		if distance != 0:
			linkRssi = self.rssi( distance )
			#print( distance )
			#print( linkRssi )
		
		if linkRSSI is not None:
			linkRssi = linkRSSI
		
		
		fileName = self.filePrefix + '0.' + str( srcSensor.getNodeId() ) + '.cfg'
		line = '0.' + str( dstSensor.getNodeId() ) + ',' + str( dstSensor.getIP() ) + ',' + str( dstSensor.getPort() ) + ',' + str( int( linkRssi ) ) + '\n'
		with open( fileName, "a" ) as nodeConfigFile:
			nodeConfigFile.write( line )
			nodeConfigFile.close()
			
		fileName = self.filePrefix + '0.' + str( dstSensor.getNodeId() ) + '.cfg'
		line = '0.' + str( srcSensor.getNodeId() ) + ',' + str( srcSensor.getIP() ) + ',' + str( srcSensor.getPort() ) + ',' + str( int( linkRssi ) ) + '\n'
		with open( fileName, "a" ) as nodeConfigFile:
			nodeConfigFile.write( line )
			nodeConfigFile.close()		
		
		#distance = srcSensor.euclideanDistance( dstSensor )
		#propagationDelay = self.propagationDelay( distance )
		
		#link.intf1.config( delay = propagationDelay )
		#link.intf2.config( delay = propagationDelay )
	
	def nextAvailableCommonSubnet( self, srcHost, dstHost ):
		srcSensor = self.sensorNodes[ srcHost ]
		dstSensor = self.sensorNodes[ dstHost ]
		nxtSubnet = srcSensor.nextAvailableSubnet()
		for i in range( 2, 254 ):
			if srcSensor.checkSubnetAvailable( i ) and dstSensor.checkSubnetAvailable( i ):
				srcSensor.allocateSubnet( i )
				dstSensor.allocateSubnet( i )
				return i
		
		return -1
	
	def rssi( self, distance ):
		rssi = -58 * math.log10( distance ) + 255
		return int( rssi )
		
	def propagationDelay( self, distance ):
		speed = 299792458
		seconds = distance / speed
		msec = seconds * 1000
		
		return msec
	
	def startSDNWISE( self, root ):
		portCnt = 7000
		for host, sensor in self.sensorNodes.iteritems():	
			netId = str( sensor.getNetId() )
			nodeId = sensor.getNodeId()
			nodePort = str( portCnt )
			fileName = self.filePrefix + '0.' + nodeId + '.cfg'
			logFileName = self.filePrefix + nodeId + '.log'
			cmd = ''
			portCnt = portCnt + 1
			if sensor.isGW():
				controllerIP = str( sensor.getControllerIP() )
				controllerPort = str( sensor.getControllerPort() )
				#proxyCmd = 'java -jar UDPProxyServer.jar ' + str( sensor.getIP() ) + ' ' + nodePort + ' ' + str( self.udpProxyPort ) + ' &'
				#sensor.getProxy().cmd( proxyCmd )
				
				switchDpid = str( sensor.getCoreNetSwitch().defaultDpid() )
				
				#rootCmd = 'java -jar ProxyServer.jar ' + controllerIP + ' ' + controllerPort + ' ' + str( self.tcpProxyPort ) + ' ' + switchDpid + ' ' + str( self.switchGWPort ) + ' ' + str( sensor.getProxyIntf().MAC() ) + ' ' + netId + ' ' + nodeId.split('.')[1] +' &'
				#self.tcpProxyPort = self.tcpProxyPort + 1
				#root.cmd( rootCmd )
				
				cmd = 'java -jar SDN-WISE_Node.jar' + ' -n ' + netId + ' -a ' + nodeId + ' -p ' + nodePort + ' -c ' +  controllerIP + ':' + controllerPort + ' -t ' + fileName + ' -sd ' + str( switchDpid )[8:] + ' -sm ' + str( sensor.getProxyIntf().MAC() ) + ' -sp ' + str( sensor.getSwitchPort() ) + ' -i ' + str( sensor.getGWIP() ) + ' -l FINEST &'
			else:
				cmd = 'java -jar SDN-WISE_Node.jar' + ' -n ' + netId + ' -a ' + nodeId + ' -p ' + nodePort + ' -t ' + fileName + ' -l FINEST &'
			
			print( 'Starting node ' + nodeId )
			print( cmd )
			host.cmd( cmd )
			#beaconCmd = 'java -jar BeaconConfiguration.jar localhost ' + nodePort + ' ' + nodeId
			#host.cmd( beaconCmd )
			
			

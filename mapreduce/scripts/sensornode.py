#!/bin/python

import math

class SensorNode( ):
	def __init__( self, nodeId, netId = 1, posX = None, posY = None ):
		self.subnets = {}
		self.posX = -1
		self.posY = -1
		self.IP = ''
		self.gwIP = ''
		self.port = 0
		self.gw = False
		self.controllerIP = ''
		self.controllerPort = 0
		self.proxy = None
		self.proxySwitch = None
		self.nodeId = nodeId
		self.netId = netId
		self.proxyIntf = None
		self.switchPort = None
		if posX is not None and posY is not None:
			self.posX = posX
			self.posY = posY
		for i in range (1, 254):
			self.subnets[ i ] = 0
			
	def setSwitchPort( self, port ):
		self.switchPort = port
	
	def getSwitchPort( self ):
		return self.switchPort
		
	def setGWIP( self, IPaddr ):
		self.gwIP = IPaddr
	
	def getGWIP( self ):
		return self.gwIP
	
	def setIP( self, IPaddr ):
		self.IP = IPaddr
	
	def getIP( self ):
		return self.IP
		
	def setGW( self, isGW, controllerNodeIP, controllerNodePort, sinkIntf, coreNetSwitch ):
		self.gw = isGW
		self.controllerIP = controllerNodeIP
		self.controllerPort = controllerNodePort
		self.proxySwitch = coreNetSwitch
		self.proxyIntf = sinkIntf
	
	def getProxyIntf( self ):
		return self.proxyIntf
	
	def getProxy( self ):
		return self.proxy
		
	def getCoreNetSwitch( self ):
		return self.proxySwitch
	
	def isGW( self ):
		return self.gw
		
	def getControllerIP( self ):
		return self.controllerIP
		
	def getControllerPort( self ):
		return self.controllerPort
		
	def setPort( self, portNumber ):
		self.port = portNumber
		
	def getPort( self ):
		return self.port
		
	def getNodeId( self ):
		return self.nodeId
		
	def getNetId( self ):
		return self.netId
	
	def nextAvailableSubnet( self, startIndex = 2 ):
		for i in range( startIndex, 254 ):
			if self.subnets[ i ] == 0:
				return i
		return -1
	
	def allocateSubnet( self, subnetIndex ):
		self.subnets[ subnetIndex ] = 1
	
	def checkSubnetAvailable( self, subnetIndex ):
		if self.subnets[ subnetIndex ] == 0:
			return True
		else:
			return False
	
	def getX( self ):
		return self.posX
	
	def getY( self ):
		return self.posY
	
	def euclideanDistance( self, other ):
		otherPosX = other.posX()
		otherPosY = other.posY()
		pow1 = math.pow( self.posX - otherPosX, 2 )
		pow2 = math.pow( self.posY - otherPosY, 2 )
		distance = math.sqrt( pow1 + pow2 )
		
		return distance
	

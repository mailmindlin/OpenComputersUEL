package li.cil.oc.server.network

import li.cil.oc.api.network.Connector

interface Distributor {
  var globalBuffer: Double
  var globalBufferSize: Double

  fun addConnector(connector: Connector): Unit

  fun removeConnector(connector: Connector): Unit

  fun changeBuffer(delta: Double): Double
}

package li.cil.oc.server.network

import li.cil.oc.api.network.ComponentConnector as NetComponentConnector

interface ComponentConnector : NetComponentConnector, Component, Connector

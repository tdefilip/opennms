/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.snmp.snmp4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.LogUtils;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.snmp.CollectionTracker;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.SnmpConfiguration;
import org.opennms.netmgt.snmp.SnmpObjId;
import org.opennms.netmgt.snmp.SnmpStrategy;
import org.opennms.netmgt.snmp.SnmpTrapBuilder;
import org.opennms.netmgt.snmp.SnmpV1TrapBuilder;
import org.opennms.netmgt.snmp.SnmpV2TrapBuilder;
import org.opennms.netmgt.snmp.SnmpV3TrapBuilder;
import org.opennms.netmgt.snmp.SnmpV3User;
import org.opennms.netmgt.snmp.SnmpValue;
import org.opennms.netmgt.snmp.SnmpValueFactory;
import org.opennms.netmgt.snmp.SnmpWalker;
import org.opennms.netmgt.snmp.TrapNotificationListener;
import org.opennms.netmgt.snmp.TrapProcessorFactory;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.MessageProcessingModel;
import org.snmp4j.mp.PduHandle;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class Snmp4JStrategy implements SnmpStrategy {

    private static Map<TrapNotificationListener, RegistrationInfo> s_registrations = new HashMap<TrapNotificationListener, RegistrationInfo>();
    
    private static boolean s_initialized = false;
    
    private Snmp4JValueFactory m_valueFactory;

    private static final boolean m_streamErrors = Boolean.getBoolean("org.opennms.netmgt.collectd.CollectableService.streamErrors");

    private static final String m_errorHost = System.getProperty("org.opennms.netmgt.collectd.CollectableService.errorHost");

    private static final Integer m_errorPort = Integer.getInteger("org.opennms.netmgt.collectd.CollectableService.errorPort");

    /**
     * Initialize for v3 communications
     */
    private static void initialize() {
        if (s_initialized) {
            return;
        }

//      LogFactory.setLogFactory(new Log4jLogFactory());
            
        MPv3.setEnterpriseID(5813);
        USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
        SecurityModels.getInstance().addSecurityModel(usm);
        
        // Enable extensibility in SNMP4J so that we can subclass some SMI classes to work around
        // agent bugs
        if (System.getProperty("org.snmp4j.smisyntaxes", null) != null) {
        	SNMP4JSettings.setExtensibilityEnabled(true);
        }
        
        if (Boolean.getBoolean("org.opennms.snmp.snmp4j.forwardRuntimeExceptions")) {
        	SNMP4JSettings.setForwardRuntimeExceptions(true);
        }
        
        s_initialized = true;
    }
    
    public Snmp4JStrategy() {
        initialize();
    }
    
    /**
     * SNMP4J createWalker implemenetation.
     * 
     * @param snmpAgentConfig
     * @param name
     * @param tracker
     */
    public SnmpWalker createWalker(SnmpAgentConfig snmpAgentConfig, String name, CollectionTracker tracker) {
        return new Snmp4JWalker(new Snmp4JAgentConfig(snmpAgentConfig), name, tracker);
    }
    
    /**
     * Not yet implemented.  Use a walker.
     */
    public SnmpValue[] getBulk(SnmpAgentConfig agentConfig, SnmpObjId[] oid) {
    	throw new UnsupportedOperationException("Snmp4JStrategy.getBulk not yet implemented.");
    }

    public SnmpValue set(final SnmpAgentConfig agentConfig, final SnmpObjId oid, final SnmpValue value) {
        if (log().isDebugEnabled()) {
            log().debug("set: OID: " + oid + " value: " + value.toString() + " for Agent: " + agentConfig);
        }
        
        final SnmpObjId[] oids = { oid };
        final SnmpValue[] values = { value };
        final SnmpValue[] retvalues = set(agentConfig, oids, values);
        
        return retvalues[0];
    }

    public SnmpValue[] set(final SnmpAgentConfig agentConfig, final SnmpObjId[] oids, final SnmpValue[] values) {
        if (log().isDebugEnabled()) {
            log().debug("set: OIDs: " + Arrays.toString(oids) + " values: " + Arrays.toString(values) + " for Agent: " + agentConfig);
        }
        
        return buildAndSendPdu(agentConfig, PDU.SET, oids, values);
    }

    /**
     * SNMP4J get helper that takes a single SnmpObjId
     * and calls get with an array.lenght =1 and returns
     * the first element of the returned array of SnmpValue.
     * 
     * @param agentConfig
     * @param oid
     *
     */
    public SnmpValue get(SnmpAgentConfig agentConfig, SnmpObjId oid) {
        if (log().isDebugEnabled()) {
            log().debug("get: OID: "+oid+" for Agent:"+agentConfig);
        }
        
        SnmpObjId[] oids = { oid };
        SnmpValue[] retvalues = get(agentConfig, oids);
        
        return retvalues[0];
    }
    
    /**
     * SnmpGet implementation.
     * 
     * @param agentConfig
     * @param oids
     * @return
     *        Returns an array of Snmp4JValues.  If the
     *        get was unsuccessful, then the first elment
     *        of the array will be null and lenth of 1. 
     */
    public SnmpValue[] get(SnmpAgentConfig agentConfig, SnmpObjId[] oids) {
        if (log().isDebugEnabled()) {
            log().debug("get: OID: "+Arrays.toString(oids)+" for Agent:"+agentConfig);
        }
        
        return buildAndSendPdu(agentConfig, PDU.GET, oids, null);
    }
    
    /**
     * SNMP4J getNext implementation
     * 
     * @param agentConfig
     * @param oid
     * 
     */
    public SnmpValue getNext(SnmpAgentConfig agentConfig, SnmpObjId oid) {
        if (log().isDebugEnabled()) {
            log().debug("getNext: OID: "+oid+" for Agent:"+agentConfig);
        }
        
        return getNext(agentConfig, new SnmpObjId[] { oid })[0];
    }
    
    /**
     * SNMP GetNext implementation.
     * 
     * @param agentConfig
     * @param oids
     * @return
     *        Returns an array of Snmp4JValues.  If the
     *        getNext was unsuccessful, then the first element
     *        of the array will be null and length of 1. 
     */
    public SnmpValue[] getNext(SnmpAgentConfig agentConfig, SnmpObjId[] oids) {
        if (log().isDebugEnabled()) {
            log().debug("getNext: OID: "+Arrays.toString(oids)+" for Agent:"+agentConfig);
        }
        
        return buildAndSendPdu(agentConfig, PDU.GETNEXT, oids, null);
    }

    private SnmpValue[] buildAndSendPdu(SnmpAgentConfig agentConfig, int type, SnmpObjId[] oids, SnmpValue[] values) {
        Snmp4JAgentConfig snmp4jAgentConfig = new Snmp4JAgentConfig(agentConfig);
        
        PDU pdu = buildPdu(snmp4jAgentConfig, type, oids, values);
        if (pdu == null) {
            return null;
        }
        
        return send(snmp4jAgentConfig, pdu, true);
    }

    /**
     * Sends and SNMP4J request pdu.  The attributes in SnmpAgentConfig should have been
     * adapted from default SnmpAgentConfig values to those compatible with the SNMP4J library.
     * 
     * @param agentConfig
     * @param pduType TODO
     * @param oids
     * @param values can be null
     * @return
     */
    protected SnmpValue[] send(Snmp4JAgentConfig agentConfig, PDU pdu, boolean expectResponse) {
        Snmp session;

        try {
            session = agentConfig.createSnmpSession();
        } catch (IOException e) {
            log().error("send: Could not create SNMP session for agent " + agentConfig + ": " + e, e);
            return new SnmpValue[] { null };
        }

        try {
            if (expectResponse) {
                try {
                    session.listen();
                } catch (IOException e) {
                    log().error("send: error setting up listener for SNMP responses: " + e, e);
                    return new SnmpValue[] { null };
                }
            }
    
            try {
                ResponseEvent responseEvent = session.send(pdu, agentConfig.getTarget());

                if (expectResponse) {
                    return processResponse(agentConfig, pdu, responseEvent);
                } else {
                    return null;
                }
            } catch (IOException e) {
                log().error("send: error during SNMP operation: " + e, e);
                return new SnmpValue[] { null };
            } catch (Throwable e) {
                log().error("send: unexpected error during SNMP operation: " + e, e);
                return new SnmpValue[] { null };
            }
        } finally {
            closeQuietly(session);
        }
    }

    protected PDU buildPdu(Snmp4JAgentConfig agentConfig, int pduType, SnmpObjId[] oids, SnmpValue[] values) {
        PDU pdu = agentConfig.createPdu(pduType);
        
        if (values == null) {
            for (SnmpObjId oid : oids) {
                pdu.add(new VariableBinding(new OID(oid.toString())));
            }
        } else {
            // TODO should this throw an exception?  This situation is fairly bogus and probably signifies a coding error.
            if (oids.length != values.length) {
                Exception e = new Exception("This is a bogus exception so we can get a stack backtrace");
                log().error("PDU to prepare has object values but not the same number as there are OIDs.  There are " + oids.length + " OIDs and " + values.length + " object values.", e);
                return null;
            }
        
            for (int i = 0; i < oids.length; i++) {
                pdu.add(new VariableBinding(new OID(oids[i].toString()), new Snmp4JValue(values[i].getType(), values[i].getBytes()).getVariable()));
            }
        }
        
        // TODO should this throw an exception?  This situation is fairly bogus.
        if (pdu.getVariableBindings().size() != oids.length) {
            Exception e = new Exception("This is a bogus exception so we can get a stack backtrace");
            log().error("Prepared PDU does not have as many variable bindings as there are OIDs.  There are " + oids.length + " OIDs and " + pdu.getVariableBindings() + " variable bindings.", e);
            return null;
        }
        
        return pdu;
    }

    /**
     * TODO: Merge this logic with {@link Snmp4JWalker.Snmp4JResponseListener#processResponse(PDU response)}
     */
    private static SnmpValue[] processResponse(Snmp4JAgentConfig agentConfig, PDU pdu, ResponseEvent responseEvent) throws IOException {
        SnmpValue[] retvalues = { null };

        if (responseEvent.getResponse() == null) {
            log().warn("processResponse: Timeout.  Agent: "+agentConfig);
            // Send error message for timeout
            sendError(agentConfig, pdu);
        } else if (responseEvent.getError() != null) {
            log().warn("processResponse: Error during get operation.  Error: "+responseEvent.getError().getLocalizedMessage(), responseEvent.getError());
        } else if (responseEvent.getResponse().getType() == PDU.REPORT) {
            log().warn("processResponse: Error during get operation.  Report returned with varbinds: "+responseEvent.getResponse().getVariableBindings());
        } else if (responseEvent.getResponse().getVariableBindings().size() < 1) {
            log().warn("processResponse: Received PDU with 0 varbinds.");
        } else if (responseEvent.getResponse().get(0).getSyntax() == SMIConstants.SYNTAX_NULL) {
            log().info("processResponse: Null value returned in varbind: " + responseEvent.getResponse().get(0));
        } else {
            retvalues = convertResponseToValues(responseEvent);

            if (log().isDebugEnabled()) {
                log().debug("processResponse: SNMP operation successful, value: "+Arrays.toString(retvalues));
            }
        }

        return retvalues;
    }

    private static SnmpValue[] convertResponseToValues(ResponseEvent responseEvent) {
        SnmpValue[] retvalues = new Snmp4JValue[responseEvent.getResponse().getVariableBindings().size()];
        
        for (int i = 0; i < retvalues.length; i++) {
            retvalues[i] = new Snmp4JValue(responseEvent.getResponse().get(i).getVariable());
        }
        
        return retvalues;
    }

    private static ThreadCategory log() {
        return ThreadCategory.getInstance(Snmp4JStrategy.class);
    }
    
    public SnmpValueFactory getValueFactory() {
        if (m_valueFactory == null) {
            m_valueFactory = new Snmp4JValueFactory();
        }
        return m_valueFactory;
    }

    public static class RegistrationInfo {
        private TrapNotificationListener m_listener;
        
        Snmp m_trapSession;
        Snmp4JTrapNotifier m_trapHandler;
        private TransportMapping m_transportMapping;
		private InetAddress m_address;
		private int m_port;
        
        RegistrationInfo(TrapNotificationListener listener, int trapPort) throws SocketException {
        	if (listener == null) throw new NullPointerException("You must specify a trap notification listener.");
        	LogUtils.debugf(this, "trapPort = %d", trapPort);
    
            m_listener = listener;
            m_port = trapPort;
        }
    
        public RegistrationInfo(final TrapNotificationListener listener, final InetAddress address, final int snmpTrapPort) {
        	if (listener == null) throw new NullPointerException("You must specify a trap notification listener.");

        	m_listener = listener;
        	m_address = address;
        	m_port = snmpTrapPort;
		}

        public void setSession(final Snmp trapSession) {
            m_trapSession = trapSession;
        }
        
        public Snmp getSession() {
            return m_trapSession;
        }
        
        public void setHandler(final Snmp4JTrapNotifier trapHandler) {
            m_trapHandler = trapHandler;
        }
        
        public Snmp4JTrapNotifier getHandler() {
            return m_trapHandler;
        }

        public InetAddress getAddress() {
        	return m_address;
        }
        
        public int getPort() {
            return m_port;
        }

        public void setTransportMapping(final TransportMapping transport) {
            m_transportMapping = transport;
        }
        
        public TransportMapping getTransportMapping() {
            return m_transportMapping;
        }
        
        public int hashCode() {
            return (m_listener.hashCode() + m_address.hashCode() ^ m_port);
        }
        
		public boolean equals(final Object obj) {
            if (obj instanceof RegistrationInfo) {
            	final RegistrationInfo info = (RegistrationInfo) obj;
                return (m_listener == info.m_listener) && Arrays.equals(m_address.getAddress(), info.getAddress().getAddress()) && m_port == info.getPort();
            }
            return false;
        }
        
    }

    public void registerForTraps(final TrapNotificationListener listener, final TrapProcessorFactory processorFactory, InetAddress address, int snmpTrapPort, List<SnmpV3User> snmpUsers) throws IOException {
    	final RegistrationInfo info = new RegistrationInfo(listener, address, snmpTrapPort);
        
    	final Snmp4JTrapNotifier m_trapHandler = new Snmp4JTrapNotifier(listener, processorFactory);
        info.setHandler(m_trapHandler);

        final UdpAddress udpAddress;
        if (address == null) {
        	udpAddress = new UdpAddress(snmpTrapPort);
        } else {
        	udpAddress = new UdpAddress(address, snmpTrapPort);
        }
        final TransportMapping transport = new DefaultUdpTransportMapping(udpAddress);
        info.setTransportMapping(transport);
        Snmp snmp = new Snmp(transport);
        snmp.addCommandResponder(m_trapHandler);

        if (snmpUsers != null) {
            for (SnmpV3User user : snmpUsers) {
                SnmpAgentConfig config = new SnmpAgentConfig();
                config.setVersion(SnmpConfiguration.VERSION3);
                config.setSecurityName(user.getSecurityName());
                config.setAuthProtocol(user.getAuthProtocol());
                config.setAuthPassPhrase(user.getAuthPassPhrase());
                config.setPrivProtocol(user.getPrivProtocol());
                config.setPrivPassPhrase(user.getPrivPassPhrase());
                Snmp4JAgentConfig agentConfig = new Snmp4JAgentConfig(config);
                UsmUser usmUser = new UsmUser(
                        agentConfig.getSecurityName(),
                        agentConfig.getAuthProtocol(),
                        agentConfig.getAuthPassPhrase(),
                        agentConfig.getPrivProtocol(),
                        agentConfig.getPrivPassPhrase()
                );
                /* This doesn't work as expected. Basically SNMP4J is ignoring the engineId
                if (user.getEngineId() == null) {
                    snmp.getUSM().addUser(agentConfig.getSecurityName(), usmUser);
                } else {
                    snmp.getUSM().addUser(agentConfig.getSecurityName(), new OctetString(user.getEngineId()), usmUser);
                }
                */
                snmp.getUSM().addUser(agentConfig.getSecurityName(), usmUser);
            }
        }

        info.setSession(snmp);
        
        s_registrations.put(listener, info);
        
        snmp.listen();
    }
    
    public void registerForTraps(final TrapNotificationListener listener, final TrapProcessorFactory processorFactory, InetAddress address, int snmpTrapPort) throws IOException {
        registerForTraps(listener, processorFactory, address, snmpTrapPort, null);
    }

    public void registerForTraps(final TrapNotificationListener listener, final TrapProcessorFactory processorFactory, final int snmpTrapPort) throws IOException {
    	registerForTraps(listener, processorFactory, null, snmpTrapPort);
    }

    public void unregisterForTraps(final TrapNotificationListener listener, InetAddress address, int snmpTrapPort) throws IOException {
        RegistrationInfo info = s_registrations.remove(listener);
        closeQuietly(info.getSession());
    }

    public void unregisterForTraps(final TrapNotificationListener listener, final int snmpTrapPort) throws IOException {
        RegistrationInfo info = s_registrations.remove(listener);
        closeQuietly(info.getSession());
    }

    public SnmpV1TrapBuilder getV1TrapBuilder() {
        return new Snmp4JV1TrapBuilder(this);
    }

    public SnmpTrapBuilder getV2TrapBuilder() {
        return new Snmp4JV2TrapBuilder(this);
    }

    public SnmpV3TrapBuilder getV3TrapBuilder() {
        return new Snmp4JV3TrapBuilder(this);
    }

    public SnmpV2TrapBuilder getV2InformBuilder() {
        return new Snmp4JV2InformBuilder(this);
    }

    public SnmpV3TrapBuilder getV3InformBuilder() {
        return new Snmp4JV3InformBuilder(this);
    }

    protected SnmpAgentConfig buildAgentConfig(String address, int port, String community, PDU pdu) throws UnknownHostException {
        SnmpAgentConfig config = new SnmpAgentConfig();
        config.setAddress(InetAddress.getByName(address));
        config.setPort(port);
        config.setVersion(pdu instanceof PDUv1 ? SnmpAgentConfig.VERSION1 : SnmpAgentConfig.VERSION2C);
        return config;
    }

    protected SnmpAgentConfig buildAgentConfig(String address, int port, int timeout, int retries, String community, PDU pdu) throws UnknownHostException {
        SnmpAgentConfig config = buildAgentConfig(address, port, community, pdu);
        config.setTimeout(timeout);
        config.setRetries(retries);
        return config;
    }

	protected SnmpAgentConfig buildAgentConfig(String address, int port, int securityLevel,
			String securityName, String authPassPhrase, String authProtocol,
			String privPassPhrase, String privProtocol, PDU pdu) throws UnknownHostException, Exception {
			
		if (! (pdu instanceof ScopedPDU)) 
				throw new Exception();

			SnmpAgentConfig config = new SnmpAgentConfig();
	        config.setAddress(InetAddress.getByName(address));
	        config.setPort(port);
	        config.setVersion(SnmpAgentConfig.VERSION3);
	        config.setSecurityLevel(securityLevel);
	        config.setSecurityName(securityName);
	        config.setAuthPassPhrase(authPassPhrase);
	        config.setAuthProtocol(authProtocol);
	        config.setPrivPassPhrase(privPassPhrase);
	        config.setPrivProtocol(privProtocol);
	        return config;

	}

	protected SnmpAgentConfig buildAgentConfig(String address, int port, int timeout, int retries, int securityLevel,
			String securityName, String authPassPhrase, String authProtocol,
			String privPassPhrase, String privProtocol, PDU pdu) throws UnknownHostException, Exception {
			
			SnmpAgentConfig config = buildAgentConfig(address, port, securityLevel, securityName, authPassPhrase, authProtocol, privPassPhrase, privProtocol, pdu);
	        config.setTimeout(timeout);
	        config.setRetries(retries);
	        return config;

	}

	
    public void sendTest(String agentAddress, int port, String community, PDU pdu) {
        for (RegistrationInfo info : s_registrations.values()) {
            if (port == info.getPort()) {
                Snmp snmp = info.getSession();
                MessageDispatcher dispatcher = snmp.getMessageDispatcher();
                TransportMapping transport = info.getTransportMapping();
                
                int securityModel = (pdu instanceof PDUv1 ? SecurityModel.SECURITY_MODEL_SNMPv1 :SecurityModel.SECURITY_MODEL_SNMPv2c);
                int messageModel = (pdu instanceof PDUv1 ? MessageProcessingModel.MPv1 : MessageProcessingModel.MPv2c);
                CommandResponderEvent e = new CommandResponderEvent(dispatcher, transport, new IpAddress(agentAddress), messageModel, 
                                                                    securityModel, community.getBytes(), 
                                                                    SecurityLevel.NOAUTH_NOPRIV, new PduHandle(), pdu, 1000, null);

                info.getHandler().processPdu(e);
            }
        }

    }

    private void closeQuietly(Snmp session) {
        if (session == null) {
            return;
        }
        
        try {
            session.close();
        } catch (IOException e) {
            ThreadCategory.getInstance(Snmp4JStrategy.class).error("error closing SNMP connection: " + e, e);
        }
    }

	public byte[] getLocalEngineID() {
		return MPv3.createLocalEngineID();
	}

    public static void sendError(Snmp4JAgentConfig agentConfig, PDU pdu) {
        if (!m_streamErrors)
            return;

        Vector<VariableBinding> bindings = pdu.getVariableBindings();
        Vector<OID> oids = new Vector<OID>();
        for (int i = 0; i < bindings.size(); ++i) {
            oids.add(bindings.get(i).getOid());
        }
        String msg = "SNMP timeout: "
            + agentConfig.getInetAddress().getHostAddress() + " "
            + oids.toString();

        // Send error message out of socket
        Socket socket = null;
        try {
            socket = new Socket(InetAddressUtils.addr(m_errorHost),
                m_errorPort.intValue());
            OutputStream out = socket.getOutputStream();
            out.write(msg.getBytes());
            out.flush();
        }
        catch (Throwable e) {
            log().warn("Error when trying to open connection to " + m_errorHost + ":" + m_errorPort.intValue() + ", dropping error message");
        }
        finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    log().warn("IOException when closing TCP performance data socket: " + e.getMessage());
                }
            }
        }
    }


}

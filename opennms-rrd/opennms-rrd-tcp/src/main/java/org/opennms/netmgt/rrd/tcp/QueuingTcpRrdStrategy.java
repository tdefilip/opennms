/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2012 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.rrd.tcp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.rrd.RrdDataSource;
import org.opennms.netmgt.rrd.RrdGraphDetails;
import org.opennms.netmgt.rrd.RrdStrategy;
import org.opennms.netmgt.rrd.tcp.TcpRrdStrategy.RrdDefinition;

/**
 * Provides a TCP socket-based implementation of RrdStrategy that pushes update
 * commands out in a simple serialized format.
 * <p>
 * The receiver of this strategy is not defined in any way. This is just a fire
 * and forget strategy. There is no way to read data back into opennms.
 * </p>
 * 
 * @author ranger
 * @version $Id: $
 */
public class QueuingTcpRrdStrategy implements RrdStrategy<TcpRrdStrategy.RrdDefinition,String> {

    private final int queueSize = Integer.getInteger("org.opennms.netmgt.rrd.tcp.QueuingTcpRrdStrategy.queueSize").intValue();
    private final BlockingQueue<PerformanceDataReading> m_queue = new LinkedBlockingQueue<PerformanceDataReading>(queueSize);
    private final ConsumerThread m_consumerThread;
    private final LogThread m_logThread;
    private final TcpRrdStrategy m_delegate;
    private int m_skippedReadings = 0;
    private long m_offers = 0;
    private long m_failedOffers = 0;
    private long m_goodOffers = 0;

    private static class PerformanceDataReading {
        private String m_filename;
        private String m_owner;
        private String m_data;
        public PerformanceDataReading(String filename, String owner, String data) {
            m_filename = filename;
            m_owner = owner;
            m_data = data;
        }
        public String getFilename() {
            return m_filename;
        }
        public String getOwner() {
            return m_owner;
        }
        public String getData() {
            return m_data;
        }
    }

    private static class LogThread extends Thread {
        private final BlockingQueue<PerformanceDataReading> m_myQueue;
        private final QueuingTcpRrdStrategy m_strategy;
        public LogThread(final QueuingTcpRrdStrategy strategy, final BlockingQueue<PerformanceDataReading> queue) {
            m_strategy = strategy;
            m_myQueue = queue;
            this.setName(this.getClass().getSimpleName());
        }
        public void run() {
            // Write a summary of strategy statistics, clear them, and sleep
            // for 300 seconds
            try {
                while (true) {
                    long offers = m_strategy.getOffers();
                    long failedOffers = m_strategy.getFailedOffers();
                    long goodOffers = m_strategy.getGoodOffers();
                    long queueChecks = m_strategy.getQueueChecks();
                    long drains = m_strategy.getDrains();
                    long data = m_strategy.getSentData();
                    long queueSize = m_myQueue.size();
                    long remaining = m_myQueue.remainingCapacity();
                    ThreadCategory.getInstance(getClass()).warn("TCP Queue: " + offers + " offers, " + failedOffers + " failed offers, " + goodOffers + " good offers; " + queueChecks + " queue checks, " + drains + " drains, " + data + " sent readings, " + queueSize + " elements in queue, " + remaining + " remaining capacity");
                    m_strategy.clearLogStats();
                    Thread.sleep(300000);
                }
            } catch (Throwable e) {
                ThreadCategory.getInstance(this.getClass()).fatal("Unexpected exception caught in QueuingTcpRrdStrategy$LogThread, closing thread", e);
            }
        }
    }

    private static class ConsumerThread extends Thread {
        private final BlockingQueue<PerformanceDataReading> m_myQueue;
        private final TcpRrdStrategy m_strategy;
        private long m_queueChecks = 0;
        private long m_drains = 0;
        private long m_data = 0;
        public ConsumerThread(final TcpRrdStrategy strategy, final BlockingQueue<PerformanceDataReading> queue) {
            m_strategy = strategy;
            m_myQueue = queue;
            this.setName(this.getClass().getSimpleName());
        }

        public void run() {
            try {
                while (true) {
                    m_queueChecks++;
                    Collection<PerformanceDataReading> sendMe = new ArrayList<PerformanceDataReading>();
                    if (m_myQueue.drainTo(sendMe) > 0) {
                        m_drains++;
                        RrdOutputSocket socket = new RrdOutputSocket(m_strategy.getHost(), m_strategy.getPort());
                        m_data += sendMe.size();
                        for (PerformanceDataReading reading : sendMe) {
                            socket.addData(reading.getFilename(), reading.getOwner(), reading.getData());
                        }
                        socket.writeData();
                    } else {
                        long sleepTime = Integer.getInteger("org.opennms.netmgt.rrd.tcp.QueuingTcpRrdStrategy.sleepTime").longValue();
                        Thread.sleep(sleepTime);
                    }
                }
            } catch (InterruptedException e) {
                ThreadCategory.getInstance(this.getClass()).warn("InterruptedException caught in QueuingTcpRrdStrategy$ConsumerThread, closing thread");
            } catch (Throwable e) {
                ThreadCategory.getInstance(this.getClass()).fatal("Unexpected exception caught in QueuingTcpRrdStrategy$ConsumerThread, closing thread", e);
            }
        }

        // Clear/get consumer thread statistics
        public void clearLogStats() {
            m_queueChecks = 0;
            m_drains = 0;
            m_data = 0;
        }
        public long getQueueChecks() {
            return m_queueChecks;
        }
        public long getDrains() {
            return m_drains;
        }
        public long getData() {
            return m_data;
        }
    }

    /**
     * <p>Constructor for QueuingTcpRrdStrategy.</p>
     *
     * @param delegate a {@link org.opennms.netmgt.rrd.tcp.TcpRrdStrategy} object.
     */
    public QueuingTcpRrdStrategy(TcpRrdStrategy delegate) {
        m_delegate = delegate;
        m_consumerThread = new ConsumerThread(delegate, m_queue);
        m_consumerThread.start();
        m_logThread = new LogThread(this, m_queue);
        m_logThread.start();
    }

    /** {@inheritDoc} */
    public void setConfigurationProperties(Properties configurationParameters) {
        m_delegate.setConfigurationProperties(configurationParameters);
    }

    /**
     * <p>getDefaultFileExtension</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDefaultFileExtension() {
        return m_delegate.getDefaultFileExtension();
    }

    /** {@inheritDoc} */
    public TcpRrdStrategy.RrdDefinition createDefinition(String creator, String directory, String rrdName, int step, List<RrdDataSource> dataSources, List<String> rraList) throws Exception {
        return new TcpRrdStrategy.RrdDefinition(directory, rrdName);
    }

    /**
     * <p>createFile</p>
     *
     * @param rrdDef a {@link org.opennms.netmgt.rrd.tcp.TcpRrdStrategy.RrdDefinition} object.
     * @throws java.lang.Exception if any.
     */
	public void createFile(RrdDefinition rrdDef,
			Map<String, String> attributeMappings) throws Exception {
		// done nothing
    }

    /** {@inheritDoc} */
    public String openFile(String fileName) throws Exception {
        return fileName;
    }

    /** {@inheritDoc} */
    public void updateFile(String fileName, String owner, String data) throws Exception {
        long offerWait = Integer.getInteger("org.opennms.netmgt.rrd.tcp.QueuingTcpRrdStrategy.offerWait").longValue();
        m_offers++;
        if (m_queue.offer(new PerformanceDataReading(fileName, owner, data), offerWait, TimeUnit.MILLISECONDS)) {
            m_goodOffers++;
            if (m_skippedReadings > 0) {
                ThreadCategory.getInstance().warn("Skipped " + m_skippedReadings + " performance data message(s) because of queue overflow");
                m_skippedReadings = 0;
            }
        } else {
            m_failedOffers++;
            m_skippedReadings++;
        }
    }

    /**
     * <p>closeFile</p>
     *
     * @param rrd a {@link java.lang.String} object.
     * @throws java.lang.Exception if any.
     */
    public void closeFile(String rrd) throws Exception {
        // Do nothing
    }

    /** {@inheritDoc} */
    public Double fetchLastValue(String rrdFile, String ds, int interval) throws NumberFormatException {
        return m_delegate.fetchLastValue(rrdFile, ds, interval);
    }

    /** {@inheritDoc} */
    public Double fetchLastValue(String rrdFile, String ds, String consolidationFunction, int interval) throws NumberFormatException {
        return m_delegate.fetchLastValue(rrdFile, ds, consolidationFunction, interval);
    }

    /** {@inheritDoc} */
    public Double fetchLastValueInRange(String rrdFile, String ds, int interval, int range) throws NumberFormatException {
        return m_delegate.fetchLastValueInRange(rrdFile, ds, interval, range);
    }

    /** {@inheritDoc} */
    public InputStream createGraph(String command, File workDir) throws IOException {
        return m_delegate.createGraph(command, workDir);
    }

    /** {@inheritDoc} */
    public RrdGraphDetails createGraphReturnDetails(String command, File workDir) throws IOException {
        return m_delegate.createGraphReturnDetails(command, workDir);
    }

    /**
     * <p>getGraphLeftOffset</p>
     *
     * @return a int.
     */
    public int getGraphLeftOffset() {
        return m_delegate.getGraphLeftOffset();
    }

    /**
     * <p>getGraphRightOffset</p>
     *
     * @return a int.
     */
    public int getGraphRightOffset() {
        return m_delegate.getGraphRightOffset();
    }

    /**
     * <p>getGraphTopOffsetWithText</p>
     *
     * @return a int.
     */
    public int getGraphTopOffsetWithText() {
        return m_delegate.getGraphTopOffsetWithText();
    }

    /**
     * <p>getStats</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getStats() {
        return m_delegate.getStats();
    }

    /** {@inheritDoc} */
    public void promoteEnqueuedFiles(Collection<String> rrdFiles) {
        m_delegate.promoteEnqueuedFiles(rrdFiles);
    }
    
    // Clear/get strategy statistics
    public void clearLogStats() {
        m_consumerThread.clearLogStats();
        m_offers = 0;
        m_goodOffers = 0;
        m_failedOffers = 0;
    }
    public long getOffers() {
        return m_offers;
    }
    public long getGoodOffers() {
        return m_goodOffers;
    }
    public long getFailedOffers() {
        return m_failedOffers;
    }
    public long getQueueChecks() {
        return m_consumerThread.getQueueChecks();
    }
    public long getDrains() {
        return m_consumerThread.getDrains();
    }
    public long getSentData() {
        return m_consumerThread.getData();
    }
}

/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.config.poller.outages;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.runners.Parameterized.Parameters;
import org.opennms.core.test.xml.XmlTest;
import org.opennms.netmgt.config.poller.outages.Interface;

public class InterfaceTest extends XmlTest<Interface> {

    public InterfaceTest(final Interface sampleObject, final String sampleXml, final String schemaFile) {
        super(sampleObject, sampleXml, schemaFile);
    }
    
    @Parameters
    public static Collection<Object[]> data() throws ParseException {
        final Interface intf1 = new Interface();
        intf1.setAddress("100.10.0.1");
        final Interface intf2 = new Interface();
        intf2.setAddress("2001:100::1");
        final Interface intf3 = new Interface();
        intf3.setAddress("match-any");
        
        return Arrays.asList(new Object[][] {
            {
                intf1,
                "<interface address='100.10.0.1'/>\n",
                "target/classes/xsds/poll-outages.xsd"
            },
            {
                intf2,
                "<interface address='2001:100::1'/>\n",
                "target/classes/xsds/poll-outages.xsd"
            },
            {
                intf3,
                "<interface address='match-any'/>\n",
                "target/classes/xsds/poll-outages.xsd"
            }
        });
    }

}
<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

--%>

<%@page language="java"
	contentType="text/html"
	session="true"
%>

<jsp:include page="/includes/header.jsp" flush="false" >
  <jsp:param name="title" value="Configure Notifications" />
  <jsp:param name="breadcrumb" value="<a href='admin/index.jsp'>Admin</a>" />
  <jsp:param name="breadcrumb" value="Configure Notifications" />
</jsp:include>

<h3>Configure Notifications</h3>

<div class="TwoColLAdmin">
  <p>
    <a href="admin/notification/noticeWizard/eventNotices.jsp">Configure Event Notifications</a>
  </p>

  <p>
    <a href="admin/notification/destinationPaths.jsp">Configure Destination Paths</a>
  </p>

  <p>
    <a href="admin/notification/noticeWizard/buildPathOutage.jsp?newRule=IPADDR+IPLIKE+*.*.*.*&showNodes=on">Configure Path Outages</a>
  </p>
</div>

<div class="TwoColRAdmin">
  <h3>Event Notifications</h3>

  <p>
    Each event can be configured to send a notification whenever that event is
    triggered. This wizard will walk you through the steps needed for
    configuring an event to send a notification.
  </p>

  <h3>Destination Paths</h3>

  <p>
    A destination path describes what users or groups will receive
    notifications, how the notifications will be sent, and who to notify
    if escalation is needed. This wizard will walk you through setting up
    a resuable list of who to contact and how to contact them, 
    which are used in the event configuration.
  </p>

  <h3>Path Outages</h3>

  <p>
    Configuring a path outage consists of selecting an IP address/service pair
    which defines the critical path to a group of nodes.  When a node down
    condition occurs for a node in the group, the critical path will be tested.
    If it fails to respond, the node down notifications will be suppressed. The
    critical path service is typically ICMP, and at this time ICMP is the only
    critical path service supported.
  </p>
</div>

<jsp:include page="/includes/footer.jsp" flush="false" />

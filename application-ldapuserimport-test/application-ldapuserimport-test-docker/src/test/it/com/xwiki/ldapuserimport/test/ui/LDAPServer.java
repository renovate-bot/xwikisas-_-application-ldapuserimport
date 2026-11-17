/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.ldapuserimport.test.ui;

import java.io.InputStream;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldif.LDIFReader;

/**
 * An in-memory LDAP server holding the users and groups the tests import, listening on a fixed port so that the
 * wiki can be configured statically to use it.
 *
 * @version $Id$
 */
final class LDAPServer
{
    static final String BASE_DN = "dc=example,dc=org";

    static final String BIND_DN = "cn=admin," + BASE_DN;

    static final String BIND_PASSWORD = "secret";

    static final int PORT = 10389;

    /**
     * The LDAP configuration of the wiki, as {@code xwiki.cfg} lines. XWiki runs on the host (Jetty standalone), so it
     * reaches the server on localhost.
     */
    static final String XWIKI_CFG = "xwiki.authentication.ldap.server=localhost\n"
        + "xwiki.authentication.ldap.port=" + PORT + "\n"
        + "xwiki.authentication.ldap.base_DN=" + BASE_DN + "\n"
        + "xwiki.authentication.ldap.bind_DN=" + BIND_DN + "\n"
        + "xwiki.authentication.ldap.bind_pass=" + BIND_PASSWORD + "\n"
        + "xwiki.authentication.ldap.UID_attr=uid\n"
        + "xwiki.authentication.ldap.fields_mapping=last_name=sn,first_name=givenName,email=mail";

    private final InMemoryDirectoryServer server;

    LDAPServer() throws Exception
    {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", PORT));
        this.server = new InMemoryDirectoryServer(config);
        try (InputStream ldif = getClass().getResourceAsStream("/ldapuserimport.ldif")) {
            this.server.importFromLDIF(true, new LDIFReader(ldif));
        }
    }

    void start() throws Exception
    {
        this.server.startListening();
    }

    void stop()
    {
        this.server.shutDown(true);
    }
}

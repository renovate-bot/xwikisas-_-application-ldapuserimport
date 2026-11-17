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
package com.xwiki.ldapuserimport.internal;

import java.io.InputStream;

import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.test.internal.MockConfigurationSource;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldif.LDIFReader;

/**
 * An in-memory LDAP server holding the users and groups of {@code ldapuserimport.ldif}.
 *
 * @version $Id$
 */
class LDAPTestServer
{
    static final String BASE_DN = "dc=example,dc=org";

    static final String DEVELOPERS_DN = "cn=developers,ou=groups," + BASE_DN;

    static final String TESTERS_DN = "cn=testers,ou=groups," + BASE_DN;

    private static final String BIND_DN = "cn=admin," + BASE_DN;

    private static final String BIND_PASSWORD = "secret";

    private final InMemoryDirectoryServer server;

    LDAPTestServer() throws Exception
    {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
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

    /**
     * @return a new LDAP configuration targeting this server, as the LDAP authenticator would read it from the wiki
     *     preferences and {@code xwiki.cfg}
     */
    XWikiLDAPConfig createConfiguration()
    {
        XWikiLDAPConfig configuration = new XWikiLDAPConfig(null, new MockConfigurationSource());
        configuration.setFinalProperty("ldap_server", "localhost");
        configuration.setFinalProperty("ldap_port", String.valueOf(this.server.getListenPort()));
        configuration.setFinalProperty("ldap_base_DN", BASE_DN);
        configuration.setFinalProperty("ldap_bind_DN", BIND_DN);
        configuration.setFinalProperty("ldap_bind_pass", BIND_PASSWORD);
        configuration.setFinalProperty("ldap_UID_attr", "uid");
        configuration.setFinalProperty("ldap_fields_mapping", "last_name=sn,first_name=givenName,email=mail");
        return configuration;
    }
}

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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.contrib.ldap.XWikiLDAPSearchAttribute;
import org.xwiki.job.JobExecutor;
import org.xwiki.test.LogLevel;
import org.xwiki.test.internal.MockConfigurationSource;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.Utils;
import com.xwiki.ldapuserimport.LDAPUserImportConfiguration;
import com.xwiki.ldapuserimport.internal.job.DefaultLDAPGroupImportJob;
import com.xwiki.ldapuserimport.job.LDAPGroupImportRequest;

import static com.xwiki.ldapuserimport.internal.LDAPTestServer.BASE_DN;
import static com.xwiki.ldapuserimport.internal.LDAPTestServer.DEVELOPERS_DN;
import static com.xwiki.ldapuserimport.internal.LDAPTestServer.TESTERS_DN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultLDAPGroupImportManager}, run against an in-memory LDAP server.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultLDAPGroupImportManagerTest
{
    private static final String GROUPS_DN = "ou=groups," + BASE_DN;

    private static LDAPTestServer ldapServer;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private DefaultLDAPGroupImportManager manager;

    @MockComponent
    private Provider<XWikiLDAPConfig> ldapConfigurationProvider;

    @MockComponent
    private LDAPUserImportConfiguration importConfiguration;

    @MockComponent
    private JobExecutor jobExecutor;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    private XWikiLDAPConfig ldapConfiguration;

    @BeforeAll
    static void beforeAll() throws Exception
    {
        ldapServer = new LDAPTestServer();
        ldapServer.start();
    }

    @AfterAll
    static void afterAll()
    {
        ldapServer.stop();
    }

    @BeforeEach
    void beforeEach(MockitoComponentManager componentManager) throws Exception
    {
        // XWikiLDAPConfig falls back on xwiki.cfg, which it looks up statically.
        componentManager.registerComponent(ConfigurationSource.class, "xwikicfg", new MockConfigurationSource());
        Utils.setComponentManager(componentManager);
        this.ldapConfiguration = ldapServer.createConfiguration();
        when(this.ldapConfigurationProvider.get()).thenReturn(this.ldapConfiguration);
        XWikiContext context = mock(XWikiContext.class);
        when(context.getWikiId()).thenReturn("xwiki");
        when(this.contextProvider.get()).thenReturn(context);
    }

    @Test
    void getImportableGroups()
    {
        Map<String, List<XWikiLDAPSearchAttribute>> groups = this.manager.getImportableGroups(GROUPS_DN,
            "(objectClass=groupOfNames)", Collections.singletonList("cn"));

        assertThat(groups.keySet(), containsInAnyOrder(DEVELOPERS_DN, TESTERS_DN));
        XWikiLDAPSearchAttribute name = groups.get(DEVELOPERS_DN).get(0);
        assertEquals("cn", name.name);
        assertEquals("developers", name.value);
    }

    @Test
    void getImportableGroupsWhenTheServerIsUnreachable()
    {
        this.ldapConfiguration.setFinalProperty("ldap_port", "1");

        assertTrue(this.manager.getImportableGroups(GROUPS_DN, "(objectClass=groupOfNames)",
            Collections.singletonList("cn")).isEmpty());

        assertThat(this.logCapture.getMessage(0), startsWith(
            "Failed to get a list of importable LDAP groups using base DN [" + GROUPS_DN + "]"));
    }

    @Test
    void importLDAPGroupsWithTheConfiguredSearch() throws Exception
    {
        when(this.importConfiguration.getGroupPageNameFormat()).thenReturn("${cn}Group");
        when(this.importConfiguration.getLDAPGroupImportSearchDN()).thenReturn(GROUPS_DN);
        when(this.importConfiguration.getLDAPGroupImportSearchFilter()).thenReturn("(cn=dev*)");
        when(this.importConfiguration.getLDAPGroupImportSearchAttributes())
            .thenReturn(Arrays.asList("cn", "description"));

        this.manager.importLDAPGroups();

        // Starting the job is what the method is for, and the job is not run by this test.
        verify(this.jobExecutor).execute(eq(DefaultLDAPGroupImportJob.JOB_TYPE),
            argThat((LDAPGroupImportRequest request) -> "${cn}Group".equals(request.getGroupPageName())
                && GROUPS_DN.equals(request.getLDAPGroupSearchDN())
                && "(cn=dev*)".equals(request.getLDAPGroupSearchFilter())
                && Arrays.asList("cn", "description").equals(request.getLDAPGroupSearchAttributes())
                && request.getId().subList(0, 3).equals(Arrays.asList("ldap", "groupImport", "xwiki"))));
    }

    @Test
    void importLDAPGroupsWithoutConfiguredFilter() throws Exception
    {
        when(this.importConfiguration.getLDAPGroupImportSearchFilter()).thenReturn("");

        this.manager.importLDAPGroups();

        // All the groups are imported.
        String allGroupsFilter = XWikiLDAPUtilsHelper.getGroupsFilter("", this.ldapConfiguration, false);
        verify(this.jobExecutor).execute(eq(DefaultLDAPGroupImportJob.JOB_TYPE),
            argThat((LDAPGroupImportRequest request) -> allGroupsFilter.equals(request.getLDAPGroupSearchFilter())));
    }
}

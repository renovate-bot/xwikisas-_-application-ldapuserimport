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
package com.xwiki.ldapuserimport.internal.job;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.contrib.ldap.XWikiLDAPSearchAttribute;
import org.xwiki.contrib.ldap.internal.DefaultLDAPDocumentHelper;
import org.xwiki.contrib.usercommon.formatter.internal.DefaultUserFormatterFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.LogLevel;
import org.xwiki.test.internal.MockConfigurationSource;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.ldapuserimport.LDAPGroupImportManager;
import com.xwiki.ldapuserimport.LDAPUserImportConfiguration;
import com.xwiki.ldapuserimport.LDAPUserImportManager;
import com.xwiki.ldapuserimport.job.LDAPGroupImportRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultLDAPGroupImportJob}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList({
    // Used to compute the group page names.
    DefaultLDAPDocumentHelper.class,
    DefaultUserFormatterFactory.class
})
class DefaultLDAPGroupImportJobTest
{
    private static final String DEVELOPERS_DN = "cn=developers,ou=groups,dc=example,dc=org";

    private static final String TESTERS_DN = "cn=testers,ou=groups,dc=example,dc=org";

    private static final DocumentReference DEVELOPERS_GROUP =
        new DocumentReference("xwiki", "XWiki", "developersGroup");

    private static final DocumentReference TESTERS_GROUP = new DocumentReference("xwiki", "XWiki", "testersGroup");

    private static final LocalDocumentReference GROUPS_CLASS = new LocalDocumentReference("XWiki", "XWikiGroups");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private DefaultLDAPGroupImportJob job;

    @MockComponent
    private LDAPGroupImportManager groupImportManager;

    @MockComponent
    private LDAPUserImportManager userImportManager;

    @MockComponent
    private LDAPUserImportConfiguration importConfiguration;

    @MockComponent
    private Provider<XWikiLDAPConfig> ldapConfigurationProvider;

    @BeforeEach
    void beforeEach() throws Exception
    {
        if (!this.oldcore.getMocker().hasComponent(ConfigurationSource.class, "xwikicfg")) {
            this.oldcore.getMocker().registerComponent(ConfigurationSource.class, "xwikicfg",
                new MockConfigurationSource());
        }
        // The testers LDAP group is already mapped to an XWiki group.
        XWikiLDAPConfig ldapConfiguration = new XWikiLDAPConfig(null, new MockConfigurationSource());
        ldapConfiguration.setFinalProperty("ldap_group_mapping", "XWiki.Testers=" + TESTERS_DN);
        when(this.ldapConfigurationProvider.get()).thenReturn(ldapConfiguration);

        Map<String, List<XWikiLDAPSearchAttribute>> importableGroups = new HashMap<>();
        importableGroups.put(DEVELOPERS_DN,
            Collections.singletonList(new XWikiLDAPSearchAttribute("cn", "developers")));
        importableGroups.put(TESTERS_DN, Collections.singletonList(new XWikiLDAPSearchAttribute("cn", "testers")));
        when(this.groupImportManager.getImportableGroups("ou=groups,dc=example,dc=org", "(objectClass=groupOfNames)",
            Collections.singletonList("cn"))).thenReturn(importableGroups);

        LDAPGroupImportRequest request = new LDAPGroupImportRequest();
        request.setLDAPGroupSearchDN("ou=groups,dc=example,dc=org");
        request.setLDAPGroupSearchFilter("(objectClass=groupOfNames)");
        request.setLDAPGroupSearchAttributes(Collections.singletonList("cn"));
        request.setGroupPageNameFormat("${uid}Group");
        this.job.initialize(request);
    }

    @Test
    void importTheGroupsNotMappedYet() throws Exception
    {
        this.job.runInternal();

        XWikiDocument developers = getDocument(DEVELOPERS_GROUP);
        assertFalse(developers.isNew());
        assertNotNull(developers.getXObject(GROUPS_CLASS));
        assertEquals(Collections.singletonList(DEVELOPERS_GROUP), this.job.getStatus().getImportedGroups());
        // Writing the mapping is the job of the user import manager.
        verify(this.userImportManager).associateGroups(new String[] { DEVELOPERS_DN }, "xwiki:XWiki.developersGroup");

        // The LDAP group already mapped is left alone.
        assertTrue(getDocument(TESTERS_GROUP).isNew());
    }

    @Test
    void existingGroupsAreNotMappedByDefault() throws Exception
    {
        createDocument(DEVELOPERS_GROUP);

        this.job.runInternal();

        assertTrue(this.job.getStatus().getImportedGroups().isEmpty());
        verify(this.userImportManager, never()).associateGroups(any(), any());
        assertThat(this.logCapture.getMessage(0),
            startsWith("Failed to import LDAP group [" + DEVELOPERS_DN + "=[{name=cn value=developers}]]"));
    }

    @Test
    void existingGroupsAreMappedWhenAllowed() throws Exception
    {
        when(this.importConfiguration.getMapOverExistingGroups()).thenReturn(true);
        createDocument(DEVELOPERS_GROUP);

        this.job.runInternal();

        assertEquals(Collections.singletonList(DEVELOPERS_GROUP), this.job.getStatus().getImportedGroups());
        verify(this.userImportManager).associateGroups(new String[] { DEVELOPERS_DN }, "xwiki:XWiki.developersGroup");
    }

    private XWikiDocument getDocument(DocumentReference reference) throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(reference, this.oldcore.getXWikiContext());
    }

    private void createDocument(DocumentReference reference) throws Exception
    {
        XWikiDocument document = getDocument(reference);
        document.setContent("Existing group");
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());
    }
}

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xwiki.ldapuserimport.LDAPUserImportConfiguration.UserImportPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultLDAPUserImportConfiguration}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class DefaultLDAPUserImportConfigurationTest
{
    private static final DocumentReference CONFIGURATION_DOCUMENT =
        new DocumentReference("xwiki", "LDAPUserImport", "WebHome");

    private static final LocalDocumentReference CONFIGURATION_CLASS =
        new LocalDocumentReference("LDAPUserImport", "LDAPUserImportConfigClass");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private DefaultLDAPUserImportConfiguration configuration;

    private BaseObject configurationObject;

    @BeforeEach
    void beforeEach() throws Exception
    {
        XWikiDocument document =
            this.oldcore.getSpyXWiki().getDocument(CONFIGURATION_DOCUMENT, this.oldcore.getXWikiContext());
        this.configurationObject = document.newXObject(CONFIGURATION_CLASS, this.oldcore.getXWikiContext());
    }

    private void save() throws Exception
    {
        XWikiDocument document = this.configurationObject.getOwnerDocument();
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());
    }

    @Test
    void defaultsWithoutConfiguration()
    {
        // The configuration page is not saved.
        assertEquals(20, this.configuration.getMaxUserImportWizardResults());
        assertEquals(UserImportPolicy.GLOBAL_ADMINS, this.configuration.getUserImportPolicy());
        assertEquals(Collections.emptyList(), this.configuration.getLDAPUserAttributes());
        assertEquals(Collections.singletonList("cn"), this.configuration.getLDAPGroupImportSearchAttributes());
        assertEquals("${uid}Group", this.configuration.getGroupPageNameFormat());
        assertEquals("", this.configuration.getGroupMembershipAttribute());
        assertEquals("", this.configuration.getOIDCIssuer());
        assertFalse(this.configuration.getAddOIDCObject());
        assertFalse(this.configuration.getTriggerGroupUpdate());
        assertFalse(this.configuration.getForceUserGroupMembershipUpdate());
    }

    @Test
    void defaultsWithEmptyConfiguration() throws Exception
    {
        save();

        assertEquals(20, this.configuration.getMaxUserImportWizardResults());
        assertEquals(UserImportPolicy.GLOBAL_ADMINS, this.configuration.getUserImportPolicy());
        assertEquals(Collections.singletonList("cn"), this.configuration.getLDAPGroupImportSearchAttributes());
        assertEquals("${uid}Group", this.configuration.getGroupPageNameFormat());
    }

    @Test
    void configuredValues() throws Exception
    {
        this.configurationObject.setLongValue("resultsNumber", 50);
        this.configurationObject.setLargeStringValue("ldapUserAttributes", "uid,mail");
        this.configurationObject.setStringValue("ldapGroupImportSearchAttributes", "cn,description");
        this.configurationObject.setStringValue("groupPageNameFormat", "${cn}");
        this.configurationObject.setIntValue("addOIDCObject", 1);
        this.configurationObject.setIntValue("triggerGroupsUpdate", 1);
        save();

        assertEquals(50, this.configuration.getMaxUserImportWizardResults());
        assertEquals(Arrays.asList("uid", "mail"), this.configuration.getLDAPUserAttributes());
        assertEquals(Arrays.asList("cn", "description"), this.configuration.getLDAPGroupImportSearchAttributes());
        assertEquals("${cn}", this.configuration.getGroupPageNameFormat());
        assertTrue(this.configuration.getAddOIDCObject());
        assertTrue(this.configuration.getTriggerGroupUpdate());
    }

    @Test
    void zeroResultsNumber() throws Exception
    {
        // The application ships with this value, which is used as it is instead of the default.
        // See bug: the shipped "resultsNumber" is 0 instead of 20.
        this.configurationObject.setLongValue("resultsNumber", 0);
        save();

        assertEquals(0, this.configuration.getMaxUserImportWizardResults());
    }

    @ParameterizedTest
    @CsvSource({
        "globalAdmin, GLOBAL_ADMINS",
        "localAdmin, GLOBAL_AND_LOCAL_ADMINS",
        "groupEditor, GROUP_EDITORS",
        "unknown, GLOBAL_ADMINS"
    })
    void getUserImportPolicy(String value, UserImportPolicy expectedPolicy) throws Exception
    {
        this.configurationObject.setStringValue("usersAllowedToImport", value);
        save();

        assertEquals(expectedPolicy, this.configuration.getUserImportPolicy());
    }

    @Test
    void configurationIsReadFromTheMainWiki() throws Exception
    {
        this.configurationObject.setLongValue("resultsNumber", 50);
        save();

        this.oldcore.getXWikiContext().setWikiId("subwiki");

        assertEquals(50, this.configuration.getMaxUserImportWizardResults());
    }
}

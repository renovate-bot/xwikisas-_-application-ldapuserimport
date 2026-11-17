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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.test.internal.MockConfigurationSource;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.web.Utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link XWikiLDAPUtilsHelper}.
 *
 * @version $Id$
 */
@ComponentTest
class XWikiLDAPUtilsHelperTest
{
    private static final Map<String, String> DEFAULT_MAPPING = new HashMap<>();

    static {
        DEFAULT_MAPPING.put("first_name", "givenName");
        DEFAULT_MAPPING.put("last_name", "sn");
        DEFAULT_MAPPING.put("email", "mail");
    }

    private XWikiLDAPConfig configuration;

    @BeforeEach
    void beforeEach(MockitoComponentManager componentManager) throws Exception
    {
        // XWikiLDAPConfig falls back on xwiki.cfg, which it looks up statically.
        componentManager.registerComponent(ConfigurationSource.class, "xwikicfg", new MockConfigurationSource());
        Utils.setComponentManager(componentManager);
        this.configuration = new XWikiLDAPConfig(null, new MockConfigurationSource());
    }

    @ParameterizedTest
    @CsvSource({
        "false, al, (&(|(uid=al*)(mail=al*))(|(objectClass=person)))",
        "true, al, (&(|(uid=*al*)(mail=*al*))(|(objectClass=person)))",
        // The searched value can't change the structure of the filter.
        "false, a*)(uid=*, (&(|(uid=a\\2a\\29\\28uid=\\2a*)(mail=a\\2a\\29\\28uid=\\2a*))(|(objectClass=person)))"
    })
    void getSearchFilter(boolean isFullSearch, String searchInput, String expectedFilter)
    {
        assertEquals(expectedFilter, XWikiLDAPUtilsHelper.getSearchFilter("person", searchInput,
            new String[] { "uid", "mail" }, isFullSearch));
    }

    @Test
    void getFilterFromObjectClasses()
    {
        assertEquals("(|(objectClass=person)(objectClass=inetOrgPerson))",
            XWikiLDAPUtilsHelper.getFilterFromObjectClasses("person,inetOrgPerson"));
    }

    @Test
    void getUsersFilterMatchesAnyObjectClassByDefault()
    {
        assertEquals("(&(|(uid=bob*)(cn=bob*))(|(objectClass=*)))",
            XWikiLDAPUtilsHelper.getUsersFilter("bob", "uid,cn", this.configuration, false));
    }

    @Test
    void getUsersFilterWithConfiguredUserClasses()
    {
        this.configuration.setFinalProperty("ldap_user_classes", "inetOrgPerson,user");

        assertEquals("(&(|(mail=*bob*))(|(objectClass=inetOrgPerson)(objectClass=user)))",
            XWikiLDAPUtilsHelper.getUsersFilter("bob", new String[] { "mail" }, this.configuration, true));
    }

    @Test
    void getGroupsFilterSearchesTheGroupNameInTheKnownGroupClassesByDefault()
    {
        assertEquals("(&(|(cn=dev*))(|(objectClass=group)(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)"
            + "(objectClass=dynamicGroup)(objectClass=dynamicGroupAux)(objectClass=groupWiseDistributionList)"
            + "(objectClass=posixGroup)(objectClass=apple-group)))",
            XWikiLDAPUtilsHelper.getGroupsFilter("dev", this.configuration, false));
    }

    @Test
    void getGroupsFilterWithConfiguredGroupClassesAndFields()
    {
        this.configuration.setFinalProperty("ldap_group_classes", "groupOfNames");
        this.configuration.setFinalProperty("ldap_group_searchfields", "cn,description");

        assertEquals("(&(|(cn=*dev*)(description=*dev*))(|(objectClass=groupOfNames)))",
            XWikiLDAPUtilsHelper.getGroupsFilter("dev", this.configuration, true));
    }

    @Test
    void getUserFieldsMapUsesCNAsUIDByDefault()
    {
        Map<String, String> fieldsMap = XWikiLDAPUtilsHelper.getUserFieldsMap(this.configuration, DEFAULT_MAPPING);

        assertEquals("uid", fieldsMap.get("cn"));
    }

    @Test
    void getUserFieldsMapMapsTheConfiguredLDAPAttributesToXWikiFields()
    {
        this.configuration.setFinalProperty("ldap_UID_attr", "sAMAccountName");
        this.configuration.setFinalProperty("ldap_fields_mapping", "first_name=givenName,email=mail");

        Map<String, String> fieldsMap = XWikiLDAPUtilsHelper.getUserFieldsMap(this.configuration, DEFAULT_MAPPING);

        assertEquals("uid", fieldsMap.get("sAMAccountName"));
        // The LDAP authenticator lower-cases the mapped attribute names.
        assertEquals("first_name", fieldsMap.get("givenname"));
        assertEquals("email", fieldsMap.get("mail"));
    }

    @Test
    void getUserAttributes()
    {
        this.configuration.setFinalProperty("ldap_UID_attr", "uid");
        this.configuration.setFinalProperty("ldap_fields_mapping", "first_name=givenName,company=o");

        String[] attributes = XWikiLDAPUtilsHelper.getUserAttributes(this.configuration, DEFAULT_MAPPING);

        // The UID, the mapped attributes and the attributes of the default mapping.
        assertThat(attributes, arrayContainingInAnyOrder("uid", "givenName", "o", "sn", "mail"));
    }

    @Test
    void getUserAttributesIncludesThePhotoWhenSynchronized()
    {
        this.configuration.setFinalProperty("ldap_UID_attr", "uid");
        this.configuration.setFinalProperty("ldap_fields_mapping", "first_name=givenName");
        this.configuration.setFinalProperty("ldap_update_photo", "1");
        this.configuration.setFinalProperty("ldap_photo_attribute", "jpegPhoto");

        String[] attributes = XWikiLDAPUtilsHelper.getUserAttributes(this.configuration, DEFAULT_MAPPING);

        assertThat(attributes, hasItemInArray("jpegPhoto"));
    }
}

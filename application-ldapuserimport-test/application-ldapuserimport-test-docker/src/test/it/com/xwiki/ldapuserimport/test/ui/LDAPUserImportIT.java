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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.rest.model.jaxb.Object;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import com.xwiki.ldapuserimport.test.po.AssociateLDAPGroupsModal;
import com.xwiki.ldapuserimport.test.po.LDAPImportUsersModal;
import com.xwiki.ldapuserimport.test.po.LDAPUserImportGroupEditPage;
import com.xwiki.ldapuserimport.test.po.LDAPUserImportGroupsPage;
import com.xwiki.ldapuserimport.test.po.LDAPUserImportUsersAdministrationSectionPage;
import com.xwiki.ldapuserimport.test.po.UpdateLDAPGroupModal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Imports users and groups from an LDAP server through the administration UI.
 *
 * @version $Id$
 */
@UITest(properties = {
    "xwikiCfgAdditionalProperties=" + LDAPServer.XWIKI_CFG,
    // The groups and group members tables of the administration are listed by the RightsManagerPlugin.
    "xwikiCfgPlugins=com.xpn.xwiki.plugin.rightsmanager.RightsManagerPlugin"
})
class LDAPUserImportIT
{
    private static final DocumentReference CONFIGURATION =
        new DocumentReference("xwiki", "LDAPUserImport", "WebHome");

    private static final String CONFIGURATION_CLASS = "LDAPUserImport.LDAPUserImportConfigClass";

    private static final String GROUP_NAME = "LDAPUserImportITGroup";

    private static final DocumentReference GROUP = new DocumentReference("xwiki", "XWiki", GROUP_NAME);

    private static final String GROUP_CLASS = "XWiki.XWikiGroups";

    private static final String GROUP_EDITOR = "LDAPUserImportGroupEditor";

    private static final String GROUP_EDITOR_PASSWORD = "pass";

    private static LDAPServer ldapServer;

    @BeforeAll
    static void beforeAll(TestUtils setup) throws Exception
    {
        ldapServer = new LDAPServer();
        ldapServer.start();

        setup.loginAsSuperAdmin();
        // The application ships with a limit of 0 results, which stops the searches after the first user found.
        setup.updateObject(CONFIGURATION, CONFIGURATION_CLASS, 0, "resultsNumber", 20);
    }

    @AfterAll
    static void afterAll()
    {
        if (ldapServer != null) {
            ldapServer.stop();
        }
    }

    @Test
    @Order(1)
    void importUsers(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        LDAPImportUsersModal importModal = LDAPUserImportUsersAdministrationSectionPage.gotoPage().openImportUsers();

        importModal.search("unknown");
        assertEquals("No users were found for the searched value!", importModal.getNoResultsMessage());

        importModal.search("al");
        assertEquals("", importModal.getError());
        assertThat(importModal.getImportableUsers(), contains("albert", "alice"));
        assertThat(importModal.getAlreadyImportedUserProfiles(), empty());
        assertFalse(importModal.hasMoreResultsMessage());

        List<String> imported = importModal.selectUser("albert").selectUser("alice").importSelected();
        assertThat(imported, contains(containsString("XWiki.albert"), containsString("XWiki.alice")));
        assertThat(imported.get(0), containsString("user profile has been created in"));

        // The profiles hold the values taken from LDAP.
        assertUserProfile(setup, "alice", "Alice", "Anderson", "alice@example.org");
        assertUserProfile(setup, "albert", "Albert", "Adams", "albert@example.org");

        // The users found by a new search are shown as already imported.
        importModal.searchInsideValues("example.org");
        assertThat(importModal.getAlreadyImportedUserProfiles(), containsInAnyOrder("XWiki.albert", "XWiki.alice"));
        assertThat(importModal.getImportableUsers(), containsInAnyOrder("bob", "dave", "eve"));
        importModal.close();
    }

    @Test
    @Order(2)
    void importUserIntoGroup(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        setup.deletePage(GROUP);
        setup.addObject(GROUP, GROUP_CLASS);

        LDAPImportUsersModal importModal = LDAPUserImportGroupEditPage.gotoPage(GROUP).openImportUsers();
        importModal.search("bob");
        assertThat(importModal.getImportableUsers(), contains("bob"));
        assertThat(importModal.selectUser("bob").importSelected(), contains(containsString("XWiki.bob")));

        assertThat(getGroupMembers(setup), contains("XWiki.bob"));
    }

    @Test
    @Order(3)
    void associateAndUpdateGroup(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        LDAPUserImportGroupsPage groupsPage = LDAPUserImportGroupsPage.gotoPage();

        AssociateLDAPGroupsModal associateModal = groupsPage.openAssociate(GROUP_NAME).search("dev");
        assertEquals("", associateModal.getError());
        assertThat(associateModal.getAssociableGroups(), contains("developers"));
        assertEquals("Groups successfully associated!", associateModal.selectGroup("developers").associate());

        // The association is stored: a new search shows the LDAP group as already associated.
        associateModal.search("dev");
        assertThat(associateModal.getAssociableGroups(), empty());
        assertThat(associateModal.getAlreadyAssociatedGroups(),
            contains("Developers (developers) is already assigned."));
        associateModal.close();

        UpdateLDAPGroupModal updateModal = LDAPUserImportGroupsPage.gotoPage().openUpdate(GROUP_NAME);
        assertEquals("", updateModal.getError());
        assertEquals("2 users have been found in the LDAP group and will be imported or updated.",
            updateModal.getStatus());
        assertEquals("Group successfully updated.", updateModal.confirm());

        // The LDAP members are imported and added to the group. Bob, who was added by hand, is kept because the
        // membership is not forced to match LDAP by default.
        assertThat(getGroupMembers(setup), containsInAnyOrder("XWiki.bob", "XWiki.dave", "XWiki.eve"));
        assertUserProfile(setup, "dave", "Dave", "Davis", "dave@example.org");
    }

    @Test
    @Order(4)
    void importUsersRights(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        setup.createUser(GROUP_EDITOR, GROUP_EDITOR_PASSWORD, null);
        setup.setRights(GROUP, "", "XWiki." + GROUP_EDITOR, "edit", true);

        try {
            // By default, only the global administrators can import users.
            setup.login(GROUP_EDITOR, GROUP_EDITOR_PASSWORD);
            assertFalse(LDAPUserImportGroupEditPage.gotoPage(GROUP).isImportUsersAvailable());

            // The application can let the editors of a group import users into it.
            setImportPolicy(setup, "groupEditor");
            setup.login(GROUP_EDITOR, GROUP_EDITOR_PASSWORD);
            assertTrue(LDAPUserImportGroupEditPage.gotoPage(GROUP).isImportUsersAvailable());
        } finally {
            setImportPolicy(setup, "globalAdmin");
        }
    }

    private void setImportPolicy(TestUtils setup, String policy)
    {
        setup.loginAsSuperAdmin();
        setup.updateObject(CONFIGURATION, CONFIGURATION_CLASS, 0, "usersAllowedToImport", policy);
    }

    private void assertUserProfile(TestUtils setup, String userName, String firstName, String lastName,
        String email) throws Exception
    {
        DocumentReference user = new DocumentReference("xwiki", "XWiki", userName);
        Map<String, String> profile = getProperties(setup.rest().get(new ObjectReference("XWiki.XWikiUsers[0]", user)));
        assertEquals(firstName, profile.get("first_name"));
        assertEquals(lastName, profile.get("last_name"));
        assertEquals(email, profile.get("email"));
    }

    private List<String> getGroupMembers(TestUtils setup) throws Exception
    {
        List<String> members = new ArrayList<>();
        int number = 0;
        ObjectReference member = new ObjectReference(GROUP_CLASS + "[0]", GROUP);
        while (setup.rest().exists(member)) {
            String name = getProperties(setup.rest().get(member)).get("member");
            if (name != null && !name.isEmpty()) {
                members.add(name);
            }
            number++;
            member = new ObjectReference(GROUP_CLASS + "[" + number + "]", GROUP);
        }
        return members;
    }

    private Map<String, String> getProperties(Object object)
    {
        return object.getProperties().stream()
            .filter(property -> property.getValue() != null)
            .collect(Collectors.toMap(Property::getName, Property::getValue));
    }
}

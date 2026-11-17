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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Provider;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheManager;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.contrib.ldap.internal.DefaultLDAPDocumentHelper;
import org.xwiki.contrib.ldap.internal.LDAPGroupsCache;
import org.xwiki.contrib.usercommon.formatter.internal.DefaultUserFormatterFactory;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.internal.MockConfigurationSource;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;
import com.xpn.xwiki.web.XWikiURLFactory;
import com.xwiki.ldapuserimport.LDAPUserImportConfiguration;
import com.xwiki.ldapuserimport.LDAPUserImportConfiguration.UserImportPolicy;

import static com.xwiki.ldapuserimport.internal.LDAPTestServer.DEVELOPERS_DN;
import static com.xwiki.ldapuserimport.internal.LDAPTestServer.TESTERS_DN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultLDAPUserImportManager}, run against an in-memory LDAP server and an in-memory wiki.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList({
    XWikiLDAPFactory.class,
    // Used by the LDAP authenticator to compute the user page names.
    DefaultLDAPDocumentHelper.class,
    DefaultUserFormatterFactory.class,
    // Used by the LDAP authenticator to get the members of the LDAP groups.
    LDAPGroupsCache.class
})
class DefaultLDAPUserImportManagerTest
{
    private static final String MAIN_WIKI = "xwiki";

    private static final String XWIKI_SPACE = "XWiki";

    private static final LocalDocumentReference USERS_CLASS = new LocalDocumentReference(XWIKI_SPACE, "XWikiUsers");

    private static final LocalDocumentReference GROUPS_CLASS = new LocalDocumentReference(XWIKI_SPACE, "XWikiGroups");

    private static final LocalDocumentReference PREFERENCES_CLASS =
        new LocalDocumentReference(XWIKI_SPACE, "XWikiPreferences");

    private static final DocumentReference GLOBAL_PREFERENCES =
        new DocumentReference(MAIN_WIKI, XWIKI_SPACE, "XWikiPreferences");

    private static final DocumentReference OIDC_CLASS =
        new DocumentReference(MAIN_WIKI, Arrays.asList(XWIKI_SPACE, "OIDC"), "UserClass");

    private static final String GROUP_NAME = "XWiki.Developers";

    private static final DocumentReference GROUP = new DocumentReference(MAIN_WIKI, XWIKI_SPACE, "Developers");

    private static final String MEMBER = "member";

    private static final String EXISTS = "exists";

    private static LDAPTestServer ldapServer;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectMockComponents
    private DefaultLDAPUserImportManager manager;

    @MockComponent
    private Provider<XWikiLDAPConfig> ldapConfigurationProvider;

    @MockComponent
    private LDAPUserImportConfiguration importConfiguration;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    private ContextualLocalizationManager localization;

    @MockComponent
    private CacheManager cacheManager;

    private XWikiContext context;

    /**
     * The LDAP group mapping, as configured in the LDAP authenticator.
     */
    private String groupMapping;

    private Integer ldapPort;

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
    void beforeEach() throws Exception
    {
        if (!this.oldcore.getMocker().hasComponent(ConfigurationSource.class, "xwikicfg")) {
            this.oldcore.getMocker().registerComponent(ConfigurationSource.class, "xwikicfg",
                new MockConfigurationSource());
        }
        // A new configuration for each call, like the real provider.
        when(this.ldapConfigurationProvider.get()).thenAnswer(invocation -> {
            XWikiLDAPConfig configuration = ldapServer.createConfiguration();
            if (this.groupMapping != null) {
                configuration.setFinalProperty("ldap_group_mapping", this.groupMapping);
            }
            if (this.ldapPort != null) {
                configuration.setFinalProperty("ldap_port", String.valueOf(this.ldapPort));
            }
            return configuration;
        });
        when(this.importConfiguration.getMaxUserImportWizardResults()).thenReturn(20);
        when(this.importConfiguration.getUserImportPolicy()).thenReturn(UserImportPolicy.GLOBAL_ADMINS);
        when(this.importConfiguration.getGroupMembershipAttribute()).thenReturn("");
        // The LDAP group members are never cached, so that each test gets them from the LDAP server.
        when(this.cacheManager.createNewCache(any())).thenReturn(mock(Cache.class));

        this.context = this.oldcore.getXWikiContext();
        // Used to compute the URL of the imported user profiles.
        this.context.setURLFactory(mock(XWikiURLFactory.class));
    }

    @Test
    void getUsersStartingWith() throws Exception
    {
        Map<String, Map<String, String>> users = this.manager.getUsers(null, null, "al", false);

        assertThat(users.keySet(), contains("albert", "alice"));
        Map<String, String> alice = users.get("alice");
        assertEquals("false", alice.get(EXISTS));
        assertEquals("XWiki.alice", alice.get("userProfile"));
        assertEquals("alice", alice.get("username"));
        assertEquals("alice", alice.get("uid"));
        // The LDAP attributes are renamed after the XWiki fields they are mapped to...
        assertEquals("Anderson", alice.get("last_name"));
        assertEquals("alice@example.org", alice.get("email"));
        // ... except the ones whose name is not in lower case, because the LDAP authenticator lower-cases the mapping.
        assertEquals("Alice", alice.get("givenName"));
        assertNull(alice.get("first_name"));
    }

    @Test
    void getUsersContaining() throws Exception
    {
        assertThat(this.manager.getUsers(null, null, "example.org", true).keySet(),
            contains("albert", "alice", "bob", "dave", "eve"));
        // A "starts with" search doesn't match in the middle of the values.
        assertThat(this.manager.getUsers(null, null, "example.org", false).keySet(), empty());
    }

    @Test
    void getUsersInSingleField() throws Exception
    {
        // Alice's last name starts with "A" too, but not with "Ad".
        assertThat(this.manager.getUsers("sn", null, "Ad", false).keySet(), contains("albert"));
        // The single field takes precedence over the list of fields.
        assertThat(this.manager.getUsers("sn", "uid", "Ad", false).keySet(), contains("albert"));
        assertThat(this.manager.getUsers(null, "uid,mail", "bob", false).keySet(), contains("bob"));
    }

    @Test
    void getUsersStopsAtTheResultsLimit() throws Exception
    {
        when(this.importConfiguration.getMaxUserImportWizardResults()).thenReturn(2);

        Map<String, Map<String, String>> users = this.manager.getUsers(null, null, "example.org", true);

        assertEquals(2, users.size());
        assertTrue(this.manager.displayedMax(users.size()));
        assertFalse(this.manager.displayedMax(1));
    }

    @Test
    void getUsersWithAZeroResultsLimit() throws Exception
    {
        // The application ships with a limit of 0, which still returns the first user found.
        // See bug: the shipped "resultsNumber" is 0 instead of the 20 the code defaults to.
        when(this.importConfiguration.getMaxUserImportWizardResults()).thenReturn(0);

        assertEquals(1, this.manager.getUsers(null, null, "example.org", true).size());
    }

    @Test
    void getUsersFlagsTheImportedUsers() throws Exception
    {
        this.manager.importUsers(new String[] { "alice" }, null);

        Map<String, Map<String, String>> users = this.manager.getUsers(null, null, "al", false);

        assertEquals("true", users.get("alice").get(EXISTS));
        assertEquals("false", users.get("albert").get(EXISTS));
    }

    @Test
    void getUsersWithoutMatch() throws Exception
    {
        assertTrue(this.manager.getUsers(null, null, "nobody", false).isEmpty());
    }

    @Test
    void getUsersWhenTheServerIsUnreachable() throws Exception
    {
        this.ldapPort = 1;
        this.context.setWikiId("subwiki");

        assertThrows(Exception.class, () -> this.manager.getUsers(null, null, "al", false));

        // The search is done on the main wiki, and the current wiki is restored afterwards.
        assertEquals("subwiki", this.context.getWikiId());
        assertThat(this.logCapture.getMessage(0), org.hamcrest.Matchers.containsString("localhost:1"));
    }

    @Test
    void importUsersIntoGroup() throws Exception
    {
        Map<String, Map<String, String>> users = this.manager.importUsers(new String[] { "alice", "albert" }, GROUP_NAME);

        assertThat(users.keySet(), contains("albert", "alice"));
        assertEquals("true", users.get("alice").get(EXISTS));
        assertEquals("XWiki.alice", users.get("alice").get("userProfile"));

        BaseObject profile = getDocument(user("alice")).getXObject(USERS_CLASS);
        assertEquals("Alice", profile.getStringValue("first_name"));
        assertEquals("Anderson", profile.getStringValue("last_name"));
        assertEquals("alice@example.org", profile.getStringValue("email"));

        assertThat(getMembers(GROUP), contains("XWiki.albert", "XWiki.alice"));
    }

    @Test
    void importUsersWithoutGroup() throws Exception
    {
        assertThat(this.manager.importUsers(new String[] { "bob" }, "").keySet(), contains("bob"));

        assertFalse(getDocument(user("bob")).isNew());
        assertTrue(getDocument(GROUP).isNew());
    }

    @Test
    void importNoUsers() throws Exception
    {
        assertTrue(this.manager.importUsers(new String[0], GROUP_NAME).isEmpty());
        assertTrue(getDocument(GROUP).isNew());
    }

    @Test
    void importUnknownUser() throws Exception
    {
        Map<String, Map<String, String>> users = this.manager.importUsers(new String[] { "nobody", "bob" }, null);

        assertThat(users.keySet(), contains("bob"));
        assertTrue(getDocument(user("nobody")).isNew());
        assertEquals("Can't find any attributes for user [null]", this.logCapture.getMessage(0));
        assertEquals("Failed to import user [nobody] from ldap to xwiki.", this.logCapture.getMessage(1));
    }

    @Test
    void importUsersWithOIDCObject() throws Exception
    {
        createOIDCClass();
        when(this.importConfiguration.getAddOIDCObject()).thenReturn(true);
        when(this.importConfiguration.getOIDCIssuer()).thenReturn("https://oidc.example.org");

        this.manager.importUsers(new String[] { "alice" }, null);

        BaseObject oidc = getDocument(user("alice")).getXObject(OIDC_CLASS);
        assertEquals("alice", oidc.getStringValue("subject"));
        assertEquals("https://oidc.example.org", oidc.getStringValue("issuer"));
    }

    @Test
    void importUsersWithOIDCObjectWhenOIDCIsNotInstalled() throws Exception
    {
        when(this.importConfiguration.getAddOIDCObject()).thenReturn(true);

        this.manager.importUsers(new String[] { "alice" }, null);

        assertNull(getDocument(user("alice")).getXObject(OIDC_CLASS));
    }

    @Test
    void importUsersWithoutOIDCObject() throws Exception
    {
        createOIDCClass();

        this.manager.importUsers(new String[] { "alice" }, null);

        assertNull(getDocument(user("alice")).getXObject(OIDC_CLASS));
    }

    @Test
    void getLDAPGroups() throws Exception
    {
        Map<String, Map<String, String>> groups = this.manager.getLDAPGroups("dev", GROUP_NAME, false);

        assertThat(groups.keySet(), contains("developers"));
        Map<String, String> developers = groups.get("developers");
        assertEquals("Developers", developers.get("description"));
        assertEquals(DEVELOPERS_DN, developers.get("dn"));
        assertEquals("false", developers.get("isAssociated"));
    }

    @Test
    void getLDAPGroupsFlagsTheAssociatedGroups() throws Exception
    {
        this.groupMapping = GROUP_NAME + "=" + DEVELOPERS_DN;

        assertEquals("true", this.manager.getLDAPGroups("dev", GROUP_NAME, false).get("developers").get("isAssociated"));
        assertEquals("false",
            this.manager.getLDAPGroups("dev", "XWiki.Testers", false).get("developers").get("isAssociated"));

        // Without an XWiki group, the group associated with the LDAP group is returned.
        Map<String, String> developers = this.manager.getLDAPGroups("dev", null, false).get("developers");
        assertEquals("true", developers.get("isAssociated"));
        assertEquals(GROUP_NAME, developers.get("xwikiGroup"));
    }

    @Test
    void getLDAPOrganizationalUnits() throws Exception
    {
        assertThat(this.manager.getLDAPGroups("peo", GROUP_NAME, false, true).keySet(), contains("people"));
        assertThat(this.manager.getLDAPGroups("o", GROUP_NAME, true, true).keySet(), contains("groups", "people"));
    }

    @Test
    void getLDAPGroupsWithoutMatch() throws Exception
    {
        // Unlike the user search, no match gives null instead of an empty map.
        assertNull(this.manager.getLDAPGroups("nothing", GROUP_NAME, false));
    }

    @Test
    void associateGroups() throws Exception
    {
        createGlobalPreferences();
        this.groupMapping = "XWiki.Testers=" + TESTERS_DN;

        assertTrue(this.manager.associateGroups(new String[] { DEVELOPERS_DN }, GROUP_NAME));

        // The existing mapping is kept.
        String savedMapping = getDocument(GLOBAL_PREFERENCES).getXObject(PREFERENCES_CLASS)
            .getLargeStringValue("ldap_group_mapping");
        assertThat(Arrays.asList(savedMapping.split("\\|")),
            containsInAnyOrder(GROUP_NAME + "=" + DEVELOPERS_DN, "XWiki.Testers=" + TESTERS_DN));
    }

    @Test
    void associateNoGroups() throws Exception
    {
        createGlobalPreferences();

        assertFalse(this.manager.associateGroups(new String[0], GROUP_NAME));

        assertEquals("", getDocument(GLOBAL_PREFERENCES).getXObject(PREFERENCES_CLASS)
            .getLargeStringValue("ldap_group_mapping"));
    }

    @Test
    void getXWikiMappedGroups() throws Exception
    {
        this.groupMapping = GROUP_NAME + "=" + DEVELOPERS_DN + "|XWiki.Testers=" + TESTERS_DN;

        assertThat(this.manager.getXWikiMappedGroups(), containsInAnyOrder(GROUP_NAME, "XWiki.Testers"));
    }

    @Test
    void updateGroup() throws Exception
    {
        this.groupMapping = GROUP_NAME + "=" + DEVELOPERS_DN;
        // Bob comes from LDAP but is not in the LDAP group, Local doesn't come from LDAP.
        this.manager.importUsers(new String[] { "bob" }, GROUP_NAME);
        addMember(createLocalUser());

        assertEquals(2, this.manager.getGroupMemberSize(GROUP_NAME));
        // See bug: updateGroup() always returns false, which the UI shows as a failure.
        assertFalse(this.manager.updateGroup(GROUP_NAME));

        // The LDAP members are imported and added, and the existing members are kept by default.
        assertThat(getMembers(GROUP), containsInAnyOrder("XWiki.bob", "XWiki.Local", "XWiki.dave", "XWiki.eve"));
        assertEquals("Dave", getDocument(user("dave")).getXObject(USERS_CLASS).getStringValue("first_name"));
    }

    @Test
    void updateGroupForcingTheMembership() throws Exception
    {
        when(this.importConfiguration.getForceUserGroupMembershipUpdate()).thenReturn(true);
        this.groupMapping = GROUP_NAME + "=" + DEVELOPERS_DN;
        this.manager.importUsers(new String[] { "bob", "dave" }, GROUP_NAME);
        addMember(createLocalUser());

        this.manager.updateGroup(GROUP_NAME);

        // Bob is removed since he is not in the LDAP group anymore, but the users not coming from LDAP are kept.
        assertThat(getMembers(GROUP), containsInAnyOrder("XWiki.Local", "XWiki.dave", "XWiki.eve"));
    }

    @Test
    void updateGroupsWhenTriggered() throws Exception
    {
        this.groupMapping = GROUP_NAME + "=" + DEVELOPERS_DN;

        this.manager.updateGroups();
        assertTrue(getDocument(GROUP).isNew());

        when(this.importConfiguration.getTriggerGroupUpdate()).thenReturn(true);
        this.manager.updateGroups();
        assertThat(getMembers(GROUP), containsInAnyOrder("XWiki.dave", "XWiki.eve"));
    }

    @Test
    void hasImportForGlobalAdmins() throws Exception
    {
        when(this.authorization.hasAccess(Right.ADMIN, GLOBAL_PREFERENCES)).thenReturn(true);

        assertTrue(this.manager.hasImport());

        // A local administrator is not a global one.
        when(this.authorization.hasAccess(Right.ADMIN, GLOBAL_PREFERENCES)).thenReturn(false);
        when(this.authorization.hasAccess(Right.ADMIN)).thenReturn(true);
        assertFalse(this.manager.hasImport());
    }

    @Test
    void hasImportForLocalAdmins() throws Exception
    {
        when(this.importConfiguration.getUserImportPolicy()).thenReturn(UserImportPolicy.GLOBAL_AND_LOCAL_ADMINS);
        when(this.authorization.hasAccess(Right.EDIT)).thenReturn(true);
        assertFalse(this.manager.hasImport());

        when(this.authorization.hasAccess(Right.ADMIN)).thenReturn(true);
        assertTrue(this.manager.hasImport());
    }

    @Test
    void hasImportForGroupEditors() throws Exception
    {
        when(this.importConfiguration.getUserImportPolicy()).thenReturn(UserImportPolicy.GROUP_EDITORS);
        assertFalse(this.manager.hasImport());

        when(this.authorization.hasAccess(Right.EDIT)).thenReturn(true);
        assertTrue(this.manager.hasImport());
    }

    private DocumentReference user(String name)
    {
        return new DocumentReference(MAIN_WIKI, XWIKI_SPACE, name);
    }

    private XWikiDocument getDocument(DocumentReference reference) throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(reference, this.context);
    }

    private List<String> getMembers(DocumentReference group) throws Exception
    {
        return getDocument(group).getXObjects(GROUPS_CLASS).stream().filter(Objects::nonNull)
            .map(object -> object.getStringValue(MEMBER)).collect(Collectors.toList());
    }

    private void addMember(String member) throws Exception
    {
        XWikiDocument group = getDocument(GROUP);
        group.newXObject(GROUPS_CLASS, this.context).setStringValue(MEMBER, member);
        this.oldcore.getSpyXWiki().saveDocument(group, this.context);
    }

    private String createLocalUser() throws Exception
    {
        XWikiDocument local = getDocument(user("Local"));
        local.newXObject(USERS_CLASS, this.context).setStringValue("first_name", "Local");
        this.oldcore.getSpyXWiki().saveDocument(local, this.context);
        return "XWiki.Local";
    }

    private void createOIDCClass() throws Exception
    {
        XWikiDocument oidcClass = getDocument(OIDC_CLASS);
        oidcClass.getXClass().addTextField("subject", "Subject", 30);
        oidcClass.getXClass().addTextField("issuer", "Issuer", 30);
        this.oldcore.getSpyXWiki().saveDocument(oidcClass, this.context);
    }

    private void createGlobalPreferences() throws Exception
    {
        XWikiDocument preferences = getDocument(GLOBAL_PREFERENCES);
        preferences.newXObject(PREFERENCES_CLASS, this.context);
        this.oldcore.getSpyXWiki().saveDocument(preferences, this.context);
    }
}

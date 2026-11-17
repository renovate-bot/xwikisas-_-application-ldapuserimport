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
package com.xwiki.ldapuserimport.script;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.job.Job;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xwiki.ldapuserimport.LDAPGroupImportManager;
import com.xwiki.ldapuserimport.LDAPUserImportManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LDAPUserImportScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
class LDAPUserImportScriptServiceTest
{
    private static final String[] USERS = new String[] { "alice" };

    private static final String[] GROUPS = new String[] { "cn=developers,dc=example,dc=org" };

    private static final String GROUP = "XWiki.Developers";

    @InjectMockComponents
    private LDAPUserImportScriptService scriptService;

    @MockComponent
    private LDAPUserImportManager userImportManager;

    @MockComponent
    private LDAPGroupImportManager groupImportManager;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @Test
    void importIsRefusedToUsersNotAllowedToImport() throws Exception
    {
        when(this.userImportManager.hasImport()).thenReturn(false);

        assertTrue(this.scriptService.getUsers(null, null, "al", false).isEmpty());
        assertTrue(this.scriptService.importUsers(USERS, GROUP).isEmpty());
        assertFalse(this.scriptService.updateGroup(GROUP));
        assertFalse(this.scriptService.associateGroups(GROUPS, GROUP));

        // Nothing is read from LDAP and nothing is written to the wiki.
        verify(this.userImportManager, never()).getUsers(any(), any(), any(), any(Boolean.class));
        verify(this.userImportManager, never()).importUsers(any(), any());
        verify(this.userImportManager, never()).updateGroup(any());
        verify(this.userImportManager, never()).associateGroups(any(), any());
    }

    @Test
    void importIsAllowedToUsersAllowedToImport() throws Exception
    {
        when(this.userImportManager.hasImport()).thenReturn(true);
        Map<String, Map<String, String>> users = Collections.singletonMap("alice", Collections.emptyMap());
        when(this.userImportManager.getUsers(null, null, "al", false)).thenReturn(users);
        when(this.userImportManager.importUsers(USERS, GROUP)).thenReturn(users);
        when(this.userImportManager.associateGroups(GROUPS, GROUP)).thenReturn(true);

        assertSame(users, this.scriptService.getUsers(null, null, "al", false));
        assertSame(users, this.scriptService.importUsers(USERS, GROUP));
        assertTrue(this.scriptService.associateGroups(GROUPS, GROUP));
    }

    @Test
    void importLDAPGroupsRequiresProgrammingRight() throws Exception
    {
        doThrow(AccessDeniedException.class).when(this.authorization).checkAccess(Right.PROGRAM);

        assertThrows(AccessDeniedException.class, () -> this.scriptService.importLDAPGroups());

        verify(this.groupImportManager, never()).importLDAPGroups();
    }

    @Test
    void importLDAPGroupsWithProgrammingRight() throws Exception
    {
        Job job = mock(Job.class);
        when(this.groupImportManager.importLDAPGroups()).thenReturn(job);

        assertSame(job, this.scriptService.importLDAPGroups());
    }
}

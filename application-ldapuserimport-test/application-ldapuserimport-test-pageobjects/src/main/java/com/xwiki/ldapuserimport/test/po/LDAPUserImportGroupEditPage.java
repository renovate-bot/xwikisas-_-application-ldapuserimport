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
package com.xwiki.ldapuserimport.test.po;

import org.openqa.selenium.By;
import org.xwiki.administration.test.po.GroupEditPage;
import org.xwiki.model.reference.DocumentReference;

/**
 * A group page in edit mode, with the "Import user from LDAP" section added by the LDAP User Import application.
 *
 * @version $Id$
 * @since 1.8.0
 */
public class LDAPUserImportGroupEditPage extends GroupEditPage
{
    private static final By IMPORT_USERS_BUTTON = By.cssSelector("#groupEditImportUserSection #importUsersButton");

    /**
     * Opens the given group in edit mode.
     *
     * @param groupReference the group page
     * @return the page
     */
    public static LDAPUserImportGroupEditPage gotoPage(DocumentReference groupReference)
    {
        GroupEditPage.gotoPage(groupReference);
        return new LDAPUserImportGroupEditPage();
    }

    /**
     * The import modal is rendered by the server only for the users allowed to import, so its absence doesn't depend
     * on the JavaScript having run.
     *
     * @return {@code true} if the current user is offered to import users from LDAP
     */
    public boolean isImportUsersAvailable()
    {
        return getDriver().hasElementWithoutWaiting(By.id("importUsersModal"));
    }

    /**
     * Opens the modal used to import users from LDAP straight into this group.
     *
     * @return the opened modal
     */
    public LDAPImportUsersModal openImportUsers()
    {
        // The section is added by JavaScript once the page is loaded.
        getDriver().waitUntilElementIsVisible(IMPORT_USERS_BUTTON);
        LDAPImportUsersModal modal = new LDAPImportUsersModal();
        getDriver().findElement(IMPORT_USERS_BUTTON).click();
        return modal.waitUntilOpened();
    }
}

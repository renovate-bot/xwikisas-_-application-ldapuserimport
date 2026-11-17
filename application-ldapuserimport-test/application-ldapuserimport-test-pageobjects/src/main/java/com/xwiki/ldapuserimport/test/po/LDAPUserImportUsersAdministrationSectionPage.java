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
import org.xwiki.administration.test.po.AdministrationSectionPage;
import org.xwiki.administration.test.po.UsersAdministrationSectionPage;

/**
 * The Users administration section, with the "Import User" button added by the LDAP User Import application.
 *
 * @version $Id$
 * @since 1.8.0
 */
public class LDAPUserImportUsersAdministrationSectionPage extends UsersAdministrationSectionPage
{
    private static final By IMPORT_USERS_BUTTON = By.cssSelector("#importUsersButton:not(.hidden)");

    /**
     * Opens the Users administration section of the current wiki.
     *
     * @return the page
     */
    public static LDAPUserImportUsersAdministrationSectionPage gotoPage()
    {
        getUtil().gotoPage(AdministrationSectionPage.getURL(ADMINISTRATION_SECTION_ID));
        return new LDAPUserImportUsersAdministrationSectionPage();
    }

    /**
     * Opens the modal used to import users from LDAP.
     *
     * @return the opened modal
     */
    public LDAPImportUsersModal openImportUsers()
    {
        // The button is added by JavaScript once the page is loaded.
        getDriver().waitUntilElementIsVisible(IMPORT_USERS_BUTTON);
        LDAPImportUsersModal modal = new LDAPImportUsersModal();
        getDriver().findElement(IMPORT_USERS_BUTTON).click();
        return modal.waitUntilOpened();
    }
}

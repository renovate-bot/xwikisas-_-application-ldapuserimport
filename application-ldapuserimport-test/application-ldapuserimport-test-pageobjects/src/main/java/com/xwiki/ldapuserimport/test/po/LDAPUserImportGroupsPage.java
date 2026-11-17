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
import org.xwiki.administration.test.po.GroupsPage;

/**
 * The Groups administration section, with the "Associate" and "Update" group actions added by the LDAP User Import
 * application.
 *
 * @version $Id$
 * @since 1.8.0
 */
public class LDAPUserImportGroupsPage extends GroupsPage
{
    private static final String GROUP_ACTION_XPATH_FORMAT =
        "//table[@id = 'groupstable']//td[contains(@class, 'name') and normalize-space(.) = '%s']"
            + "/following-sibling::td[contains(@class, 'actions')]/a[contains(@class, 'action%s')]";

    private static final String ASSOCIATE_ACTION = "associategroups";

    private static final String UPDATE_ACTION = "updategroup";

    /**
     * Opens the Groups administration section and waits for the groups to be listed.
     *
     * @return the page
     */
    public static LDAPUserImportGroupsPage gotoPage()
    {
        getUtil().gotoPage(AdministrationSectionPage.getURL("Groups"));
        LDAPUserImportGroupsPage page = new LDAPUserImportGroupsPage();
        page.getGroupsTable().waitUntilReady();
        return page;
    }

    /**
     * Opens the modal used to associate LDAP groups with the given XWiki group.
     *
     * @param groupName the name of the XWiki group, as listed in the table
     * @return the opened modal
     */
    public AssociateLDAPGroupsModal openAssociate(String groupName)
    {
        By action = getActionLocator(groupName, ASSOCIATE_ACTION);
        // The action is added by JavaScript once the table is displayed.
        getDriver().waitUntilElementIsVisible(action);
        AssociateLDAPGroupsModal modal = new AssociateLDAPGroupsModal();
        getDriver().findElement(action).click();
        return modal.waitUntilOpened();
    }

    /**
     * Opens the modal used to synchronize the given XWiki group with the LDAP groups associated with it, and waits for
     * the number of LDAP members to be computed.
     *
     * @param groupName the name of the XWiki group, as listed in the table
     * @return the opened modal
     */
    public UpdateLDAPGroupModal openUpdate(String groupName)
    {
        By action = getActionLocator(groupName, UPDATE_ACTION);
        // The action is added by JavaScript, after asking the server which groups are associated with LDAP groups.
        getDriver().waitUntilElementIsVisible(action);
        UpdateLDAPGroupModal modal = new UpdateLDAPGroupModal();
        getDriver().findElement(action).click();
        return modal.waitUntilMembersCounted();
    }

    private By getActionLocator(String groupName, String action)
    {
        return By.xpath(String.format(GROUP_ACTION_XPATH_FORMAT, groupName, action));
    }
}

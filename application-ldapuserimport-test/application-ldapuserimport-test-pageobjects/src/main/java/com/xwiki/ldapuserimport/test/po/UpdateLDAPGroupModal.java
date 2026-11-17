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
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseModal;

/**
 * The "Import/update group" modal, used to synchronize an XWiki group with the LDAP groups associated with it.
 *
 * @version $Id$
 * @since 1.8.0
 */
public class UpdateLDAPGroupModal extends BaseModal
{
    private static final By STATUS = By.id("updateGroupStatus");

    private static final By ERROR = By.cssSelector("#updateGroupModal .ldapError");

    private static final By CONFIRM_BUTTON = By.id("confirmGroupUpdate");

    /**
     * Creates the page object. It must be created before the modal is opened.
     */
    public UpdateLDAPGroupModal()
    {
        super(By.id("updateGroupModal"));
    }

    /**
     * Waits until the modal is shown and the number of LDAP members of the group is displayed.
     *
     * @return this modal
     */
    public UpdateLDAPGroupModal waitUntilMembersCounted()
    {
        getDriver().waitUntilCondition(driver -> isDisplayed()
            && (this.container.findElement(CONFIRM_BUTTON).isDisplayed()
            || this.container.findElement(ERROR).isDisplayed()));
        return this;
    }

    /**
     * @return the message telling how many LDAP users will be imported or updated, or the result of the update once
     *     confirmed
     */
    public String getStatus()
    {
        return this.container.findElement(STATUS).getText();
    }

    /**
     * @return the error shown when the communication with the server failed, or an empty string when there is none
     */
    public String getError()
    {
        WebElement error = this.container.findElement(ERROR);
        return error.isDisplayed() ? error.getText() : "";
    }

    /**
     * Confirms the update and waits for it to end.
     *
     * @return the message telling whether the update succeeded
     */
    public String confirm()
    {
        this.container.findElement(CONFIRM_BUTTON).click();
        // The confirm button is hidden once the update succeeded.
        getDriver().waitUntilCondition(driver -> !this.container.findElement(CONFIRM_BUTTON).isDisplayed()
            || this.container.findElement(ERROR).isDisplayed());
        return getStatus();
    }
}

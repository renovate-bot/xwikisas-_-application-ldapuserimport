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

import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.xwiki.test.ui.po.BaseModal;

/**
 * The "Associate LDAP Group" modal, used to map LDAP groups, organizational units or filters to an XWiki group.
 *
 * @version $Id$
 * @since 1.8.0
 */
public class AssociateLDAPGroupsModal extends BaseModal
{
    /**
     * What the XWiki group can be associated with.
     */
    public enum AssociationType
    {
        /** An LDAP group. */
        GROUP("getLDAPGroups"),

        /** An LDAP organizational unit. */
        ORGANIZATIONAL_UNIT("getLDAPOus"),

        /** An LDAP filter. */
        FILTER("getFilterUsers");

        private final String value;

        AssociationType(String value)
        {
            this.value = value;
        }
    }

    private static final By SEARCH_RESULTS = By.id("groupSearchResults");

    private static final By ERROR = By.cssSelector("#ldapGroupSearchBox .ldapError");

    private AssociationType associationType = AssociationType.GROUP;

    /**
     * Creates the page object. It must be created before the modal is opened.
     */
    public AssociateLDAPGroupsModal()
    {
        super(By.id("associateGroupsModal"));
    }

    /**
     * Waits until the modal is shown.
     *
     * @return this modal
     */
    public AssociateLDAPGroupsModal waitUntilOpened()
    {
        getDriver().waitUntilCondition(driver -> isDisplayed());
        return this;
    }

    /**
     * Chooses what to associate the XWiki group with.
     *
     * @param type the association type
     * @return this modal
     */
    public AssociateLDAPGroupsModal setAssociationType(AssociationType type)
    {
        new Select(this.container.findElement(By.id("associationType"))).selectByValue(type.value);
        this.associationType = type;
        return this;
    }

    /**
     * Searches for the LDAP groups or organizational units whose name starts with the given text, and waits for the
     * results.
     *
     * @param text the text to search for
     * @return this modal
     */
    public AssociateLDAPGroupsModal search(String text)
    {
        WebElement searchInput = getAssociationTypePanel().findElement(By.name("searchInput"));
        searchInput.clear();
        searchInput.sendKeys(text);
        // The results are hidden as soon as the search starts and shown again when the search ends.
        getAssociationTypePanel().findElement(By.id("triggerGroupsSearch")).click();
        waitForResultsOrError();
        return this;
    }

    /**
     * @return the names of the found LDAP groups or organizational units that are not associated yet
     */
    public List<String> getAssociableGroups()
    {
        return this.container.findElements(By.cssSelector("#groupsList li.notAssociated input[name='group']")).stream()
            .map(checkbox -> checkbox.getAttribute("id")).collect(Collectors.toList());
    }

    /**
     * @return the messages describing the found LDAP groups or organizational units that are already associated
     */
    public List<String> getAlreadyAssociatedGroups()
    {
        return this.container.findElements(By.cssSelector("#groupsList li.associated")).stream()
            .map(WebElement::getText).collect(Collectors.toList());
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
     * Selects a found LDAP group or organizational unit for association.
     *
     * @param name the name of the group or organizational unit, as found
     * @return this modal
     */
    public AssociateLDAPGroupsModal selectGroup(String name)
    {
        WebElement checkbox = this.container.findElement(
            By.cssSelector(String.format("#groupsList input[name='group'][id='%s']", name)));
        if (!checkbox.isSelected()) {
            checkbox.click();
        }
        return this;
    }

    /**
     * Associates the selected LDAP groups with the XWiki group and waits for the association to end.
     *
     * @return the message telling whether the association succeeded
     */
    public String associate()
    {
        this.container.findElement(By.id("associateButton")).click();
        waitForResultsOrError();
        return this.container.findElement(SEARCH_RESULTS).getText();
    }

    private WebElement getAssociationTypePanel()
    {
        return this.container.findElement(By.id(this.associationType.value));
    }

    private void waitForResultsOrError()
    {
        getDriver().waitUntilCondition(driver -> this.container.findElement(SEARCH_RESULTS).isDisplayed()
            || this.container.findElement(ERROR).isDisplayed());
    }
}

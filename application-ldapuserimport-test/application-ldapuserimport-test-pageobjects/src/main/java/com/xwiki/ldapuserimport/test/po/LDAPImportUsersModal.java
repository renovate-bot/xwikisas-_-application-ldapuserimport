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
import org.xwiki.test.ui.po.BaseModal;

/**
 * The "Import User" modal, used to search for users in LDAP and to create their XWiki profiles.
 *
 * @version $Id$
 * @since 1.8.0
 */
public class LDAPImportUsersModal extends BaseModal
{
    private static final By SEARCH_RESULTS = By.id("searchResults");

    private static final By ERROR = By.cssSelector("#ldapSearchBox .ldapError");

    /**
     * Creates the page object. It must be created before the modal is opened.
     */
    public LDAPImportUsersModal()
    {
        super(By.id("importUsersModal"));
    }

    /**
     * Waits until the modal is shown.
     *
     * @return this modal
     */
    public LDAPImportUsersModal waitUntilOpened()
    {
        getDriver().waitUntilCondition(driver -> isDisplayed());
        return this;
    }

    /**
     * Searches for the LDAP users having a field value that starts with the given text, and waits for the results.
     *
     * @param text the text to search for
     * @return this modal
     */
    public LDAPImportUsersModal search(String text)
    {
        return search(text, false);
    }

    /**
     * Searches for the LDAP users having a field value that contains the given text, and waits for the results.
     *
     * @param text the text to search for
     * @return this modal
     */
    public LDAPImportUsersModal searchInsideValues(String text)
    {
        return search(text, true);
    }

    private LDAPImportUsersModal search(String text, boolean insideValues)
    {
        WebElement searchInput = this.container.findElement(By.cssSelector("#searchLDAPUsers input[name='searchInput']"));
        searchInput.clear();
        searchInput.sendKeys(text);
        WebElement searchType = this.container.findElement(By.cssSelector("#searchLDAPUsers input[name='searchType']"));
        if (searchType.isSelected() != insideValues) {
            searchType.click();
        }
        // The results are hidden as soon as the search starts and shown again when the search ends.
        this.container.findElement(By.id("triggerSearch")).click();
        waitForResultsOrError();
        return this;
    }

    /**
     * @return the LDAP UIDs of the found users that don't have an XWiki profile yet
     */
    public List<String> getImportableUsers()
    {
        return this.container.findElements(By.cssSelector("#usersList li.notImported input[name='user']")).stream()
            .map(checkbox -> checkbox.getAttribute("value")).collect(Collectors.toList());
    }

    /**
     * @return the profile pages (e.g. {@code XWiki.alice}) of the found users that were already imported
     */
    public List<String> getAlreadyImportedUserProfiles()
    {
        return this.container.findElements(By.cssSelector("#usersList li.imported a")).stream()
            .map(WebElement::getText).collect(Collectors.toList());
    }

    /**
     * @return the message shown when the search returned no users
     */
    public String getNoResultsMessage()
    {
        return this.container.findElement(SEARCH_RESULTS).getText();
    }

    /**
     * @return {@code true} if the modal says there are more results than the ones displayed
     */
    public boolean hasMoreResultsMessage()
    {
        return this.container.findElement(By.cssSelector("#ldapSearchBox .infomessage")).isDisplayed();
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
     * Selects a found user for import.
     *
     * @param uid the LDAP UID of the user
     * @return this modal
     */
    public LDAPImportUsersModal selectUser(String uid)
    {
        WebElement checkbox = this.container.findElement(
            By.cssSelector(String.format("#usersList input[name='user'][value='%s']", uid)));
        if (!checkbox.isSelected()) {
            checkbox.click();
        }
        return this;
    }

    /**
     * Imports the selected users and waits for the import to end.
     *
     * @return the messages listing the created user profiles
     */
    public List<String> importSelected()
    {
        this.container.findElement(By.id("importButton")).click();
        waitForResultsOrError();
        return this.container.findElements(By.cssSelector("#importedUsersList li")).stream()
            .map(WebElement::getText).collect(Collectors.toList());
    }

    private void waitForResultsOrError()
    {
        getDriver().waitUntilCondition(driver -> this.container.findElement(SEARCH_RESULTS).isDisplayed()
            || this.container.findElement(ERROR).isDisplayed());
    }
}

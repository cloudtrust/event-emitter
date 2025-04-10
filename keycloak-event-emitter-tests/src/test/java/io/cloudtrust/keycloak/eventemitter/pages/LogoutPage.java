package io.cloudtrust.keycloak.eventemitter.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import io.cloudtrust.keycloak.test.pages.AbstractPage;

public class LogoutPage extends AbstractPage {
    @FindBy(id = "kc-logout")
    private WebElement logoutButton;

    @Override
    public boolean isCurrent() {
        try {
            driver.findElement(By.id("kc-logout"));
            return true;
        } catch (Throwable t) {
        }
        return false;
    }

    public void logout() {
    	logoutButton.click();
    }
}

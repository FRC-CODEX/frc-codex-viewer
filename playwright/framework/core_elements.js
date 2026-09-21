import { expect } from '@playwright/test';

export class Element {
    #codexPage;
    #name;

    /**
     * Creates an instance of Element.
     * @param {CodexPage} codexPage - The page viewer object.
     * @param {string} xpathSelector - The XPath selector for the element.
     * @param {string} name - Name to represent this element in logging.
     */
    constructor(codexPage, xpathSelector, name) {
        this.#codexPage = codexPage;
        this.locator = codexPage.page.locator('xpath=' + xpathSelector);
        this.#name = name;
    }

    /**
     * Gets the CodexPage object.
     * @returns {CodexPage}
     */
    get codexPage() { return this.#codexPage; }

    /**
     * Gets the name of the element.
     * @returns {string}
     */
    get name() { return this.#name; }

    /**
     * Asserts the element is visible in the page.
     * @returns {Promise<void>}
     */
    async assertVisible() {
        this.codexPage.log(`Asserting ${this.name} is visible`);
        await expect(this.locator).toBeVisible();
    }

    /**
     * Scrolls to the element.
     * @returns {Promise<void>}
     */
    async scrollToElement() {
        this.codexPage.log(`Scrolling to ${this.name}`);
        await this.locator.scrollIntoViewIfNeeded();
    }
}

export class Button extends Element {
    /**
     * Clicks the button element.
     * @returns {Promise<void>}
     */
    async select() {
        this.codexPage.log(`Select ${this.name}`);
        await this.locator.click();
    }
}

export class Dropdown extends Element {
    /**
     * Asserts the value of the dropdown element matches the expected value.
     * @param {string} expected - The expected value of the dropdown element.
     * @returns {Promise<void>}
     */
    async assertValue(expected) {
        this.codexPage.log(`Asserting value of ${this.name} equals "${expected}"`);
        await expect(this.locator).toHaveValue(expected);
    }

    /**
     * Selects an option from the dropdown element by its value.
     * @param {string} option - The value of the option to select.
     * @returns {Promise<void>}
     */
    async selectOption(option) {
        this.codexPage.log(`Selecting "${option}" from ${this.name}`);
        await this.locator.selectOption({ value: option });
    }
}

export class Link extends Element {
    /**
     * Clicks the link element.
     * @returns {Promise<void>}
     */
    async select() {
        this.codexPage.log(`Select ${this.name}`);
        await this.locator.click();
    }

    /**
     * Gets the text content of the element.
     * @returns {Promise<string>}
     */
    async getText() {
        return await this.locator.textContent();
    }
}

export class Text extends Element {
    /**
     * Gets the text content of the element.
     * @returns {Promise<string>}
     */
    async getText() {
        this.codexPage.log(`Getting text content of ${this.name}`);
        return await this.locator.textContent();
    }
}

export class TextInput extends Element {
    /**
     * Asserts the content of the text input element matches the expected text.
     * Will wait for the content to match if needed.
     * @param {string} expectedText
     * @returns {Promise<void>}
     */
    async assertContent(expectedText) {
        this.codexPage.log(`Asserting content of ${this.name} equals "${expectedText}"`);
        await expect(this.locator).toHaveValue(expectedText);
    }

    /**
     * Clears the text input element.
     * @returns {Promise<void>}
     */
    async clear() {
        this.codexPage.log(`Clearing ${this.name}`);
        await this.locator.clear();
    }

    /**
     * Enters text into the text input element, then asserts the content of the input element
     * matches the text provided.
     * @param {string} text - The text to enter into the input element.
     * @param {boolean} pressEnter - Whether to press the Enter key after entering the text.
     * @returns {Promise<void>}
     */
    async enterText(text, pressEnter = false) {
        this.codexPage.log(`Entering "${text}" into ${this.name}`);
        await this.locator.pressSequentially(text);
        await this.assertContent(text);
        if (pressEnter) { await this.locator.press('Enter'); }
    }
}

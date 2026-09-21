import { getElementByXpath, getProperty, getTextContent, waitFor } from './utils.js';
import { CodexPage} from './codex_page.js';
import { expect } from '@jest/globals';
import { ElementHandle } from 'puppeteer-core';


export class Element {
    #codexPage;
    #xpathSelector;
    #name;

    /**
     * Creates an instance of Element.
     * @param {CodexPage} codexPage - The page viewer object.
     * @param {string} xpathSelector - The XPath selector for the element.
     * @param {string} name - Name to represent this element in logging.
     */
    constructor(codexPage, xpathSelector, name) {
        this.#codexPage = codexPage;
        this.#xpathSelector = xpathSelector;
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
        const element = await this.getElement();
        const isVisible = await element.isVisible();
        expect(isVisible).toBe(true);
    }

    /**
     * Gets the element by its XPath selector.
     * @returns {Promise<ElementHandle<Element>>}
     */
    async getElement() {
        return await getElementByXpath(this.#codexPage.page, this.#xpathSelector);
    }

    /**
     * Scrolls to the element.
     * @returns {Promise<void>}
     */
    async scrollToElement() {
        this.codexPage.log(`Scrolling to ${this.name}`);
        let element = await this.getElement();
        await element.scrollIntoView();
    }
}


export class Button extends Element {

    /**
     * Creates an instance of Button.
     * @param {CodexPage} codexPage - The page viewer object.
     * @param {string} xpathSelector - The XPath selector for the element.
     * @param {string} name - Name to represent this element in logging.
     */
    constructor(codexPage, xpathSelector, name) {
        super(codexPage, xpathSelector, name);
    }

    /**
     * Clicks the button element.
     * @returns {Promise<void>}
     */
    async select() {
        this.codexPage.log(`Select ${this.name}`);
        const button = await this.getElement();
        await button.click();
    }
}

export class Dropdown extends Element {

    /**
     * Creates an instance of Dropdown.
     * @param {CodexPage} codexPage - The page viewer object.
     * @param {string} xpathSelector - The XPath selector for the element.
     * @param {string} name - Name to represent this element in logging
     */
    constructor(codexPage, xpathSelector, name) {
        super(codexPage, xpathSelector, name);
    }

    /** Asserts the value of the dropdown element matches the expected value.
     * @param {string} expected - The expected value of the dropdown element.
     * @returns {Promise<void>}
     */
    async assertValue(expected) {
        this.codexPage.log(`Getting value of ${this.name}`);
        await waitFor(async () => {
            const dropdown = await this.getElement();
            const value = await getProperty(dropdown, 'value');
            expect(value).toEqual(expected);
        });
    }

    /**
     * Selects an option from the dropdown element.
     * @param {string} option - The option to select from the dropdown element.
     * @returns {Promise<void>}
     */
    async selectOption(option) {
        this.codexPage.log(`Selecting ${option} from ${this.name}`);
        const dropdown = await this.getElement();
        await dropdown.select(option);

    }
}

export class Link extends Element {

    /**
     * Creates an instance of Link.
     * @param {CodexPage} codexPage - The page viewer object.
     * @param {string} xpathSelector - The XPath selector for the element.
     * @param {string} name - Name to represent this element in logging.
     */
    constructor(codexPage, xpathSelector, name) {
        super(codexPage, xpathSelector, name);
    }

    /**
     * Clicks the link element.
     * @returns {Promise<void>}
     */
    async select() {
        this.codexPage.log(`Select ${this.name}`);
        const link = await this.getElement();
        await link.click();
    }

    /**
     * Gets the text content of the element.
     * @returns {Promise<string>}
     */
    async getText() {
        const link = await this.getElement();
        return await getTextContent(link);
    }
}

export class Text extends Element {

    /**
     * Creates an instance of Text.
     * @param {CodexPage} codexPage - The page viewer object.
     * @param {string} xpathSelector - The XPath selector for the element.
     * @param {string} name - Name to represent this element in logging.
     */
    constructor(codexPage, xpathSelector, name) {
        super(codexPage, xpathSelector, name);
    }

    /**
     * Gets the text content of the element.
     * @returns {Promise<string>}
     */
    async getText() {
        this.codexPage.log(`Getting text content of ${this.name}`);
        const elem = await this.getElement();
        return await getTextContent(elem);
    }
}

export class TextInput extends Element {

    /**
     * Creates an instance of TextInput.
     * @param {CodexPage} codexPage - The page viewer object.
     * @param {string} xpathSelector - The XPath selector for the element.
     * @param {string} name - Name to represent this element in logging.
     */
    constructor(codexPage, xpathSelector, name) {
        super(codexPage, xpathSelector, name);
    }

    /**
     * Asserts the content of the text input element matches the expected text.
     * Will wait for the content to match if needed
     * @param {string} expectedText
     * @returns {Promise<void>}
     */
    async assertContent(expectedText) {
        this.codexPage.log(`Asserting content of ${this.name} equals "${expectedText}"`);
        await waitFor(async () => {
            const input = await this.getElement();
            const property = await input.getProperty('value');
            const value = await property.jsonValue();
            expect(value).toEqual(expectedText);
        });
    }

    /**
     * Clears the text input element by selecting all text via triple click, then
     * presses the Backspace key.
     * @returns {Promise<void>}
     */
    async clear() {
        this.codexPage.log(`Clearing ${this.name}`);
        const input = await this.getElement();
        await input.click({ clickCount: 3 });
        await input.press('Backspace');
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
        const input = await this.getElement();
        await input.type(text);
        await this.assertContent(text);
        if (pressEnter){ await input.press('Enter'); }
    }
}

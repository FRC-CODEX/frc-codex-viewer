import { Button, Dropdown, Link, Text, TextInput } from '../core_elements.js';
import { expect } from '@playwright/test';

const RESULT_CARDS_XPATH = '//*[contains(@id,"result")]';

const CARD_FIELD_XPATHS = {
    companyName: '//h3',
    crn: `//dt[span[contains(text(), 'CRN')]]/following-sibling::dd`,
    documentDate: `//dt[span[contains(text(), 'Document Date:')]]/following-sibling::dd`,
    filingDate: `//dt[span[contains(text(), 'Date Filed:')]]/following-sibling::dd`,
};

/**
 * Represents the search options on the UK iXBRL Viewer page.
 */
export class Search {
    #codexPage;
    advancedSearch;

    constructor(codexPage) {
        this.#codexPage = codexPage;
        this.advancedSearch = new AdvancedSearch(this.#codexPage);
        this.companyNameAndNumberInput = new TextInput(this.#codexPage,
            '//*[@id="company-name-or-number"]', 'Company Name or Number');
        this.registry = new Dropdown(this.#codexPage,'//*[@id="registryCode"]', 'Registry Code');
        this.submitButton = new Button(this.#codexPage,
            '//button[@type="submit"]', 'Submit');
    }

    /**
     * Asserts that the search results contain the expected number of results.
     * @param expectedCount
     * @returns {Promise<void>}
     */
    async assertResultCount(expectedCount) {
        this.#codexPage.log(`Asserting result count is ${expectedCount}`);
        await expect(this.#resultCards).toHaveCount(expectedCount);
    }

    /**
     * Returns the first search result card with the specified name, crn, doc date, and/or filing date.
     * all values are optional and will be ignored if not provided.
     * @param {string} name - The name of the company to search for.
     * @param {string} crn - The company registration number to search for.
     * @param {string} docDate - The document date to search for.
     * @param {string} filingDate - The filing date to search for.
     * @returns {Promise<SearchResultCard>} - The search result card that matches the specified criteria.
     */
    async getSearchResult(name = '', crn = '', docDate = '', filingDate = '') {
        this.#codexPage.log(`Finding search result with name: ${name}, crn: ${crn}, doc date: ${docDate}, filing date: ${filingDate}`);
        const conditions = [
            [CARD_FIELD_XPATHS.companyName, name],
            [CARD_FIELD_XPATHS.crn, crn],
            [CARD_FIELD_XPATHS.documentDate, docDate],
            [CARD_FIELD_XPATHS.filingDate, filingDate],
        ]
            .filter(([, value]) => value !== '')
            .map(([fieldXpath, value]) => `.${fieldXpath}[.="${value}"]`);
        const predicate = conditions.length > 0 ? `[${conditions.join(' and ')}]` : '';
        const cardXpath = `(${RESULT_CARDS_XPATH}${predicate})[1]`;
        await expect(this.#codexPage.page.locator('xpath=' + cardXpath)).toBeVisible({ timeout: 30000 });
        return new SearchResultCard(this.#codexPage, cardXpath);
    }

    get #resultCards() {
        return this.#codexPage.page.locator('xpath=' + RESULT_CARDS_XPATH);
    }
}

/**
 * Represents the advanced search options on the UK iXBRL Viewer page.
 */
export class AdvancedSearch {
    #codexPage;

    constructor(codexPage) {
        this.#codexPage = codexPage;
        this.minFilingDateYear = new TextInput(this.#codexPage,
            '//*[@id="min-filing-date-year"]', 'Min Date Filed Year');
        this.minFilingDateMonth = new TextInput(this.#codexPage,
            '//*[@id="min-filing-date-month"]', 'Min Date Filed Month');
        this.minFilingDateDay = new TextInput(this.#codexPage,
            '//*[@id="min-filing-date-day"]', 'Min Date Filed Day');
        this.maxFilingDateYear = new TextInput(this.#codexPage,
            '//*[@id="max-filing-date-year"]', 'Max Date Filed Year');
        this.maxFilingDateMonth = new TextInput(this.#codexPage,
            '//*[@id="max-filing-date-month"]', 'Max Date Filed Month');
        this.maxFilingDateDay = new TextInput(this.#codexPage,
            '//*[@id="max-filing-date-day"]', 'Max Date Filed Day');
    }
}

/**
 * Represents a search result card on the UK iXBRL Viewer page.
 */
export class SearchResultCard {
    #codexPage;
    companyName;
    crn;
    registry;
    documentDate;
    filingButton;
    csvButton;
    jsonButton;
    filingDate;
    #locator;
    viewerButton;

    constructor(codexPage, locator) {
        this.#codexPage = codexPage;
        this.#locator = locator;
        this.companyName = new Link(this.#codexPage,
            `${this.#locator}${CARD_FIELD_XPATHS.companyName}`, 'Company Name');
        this.crn = new Link(this.#codexPage,
            `${this.#locator}${CARD_FIELD_XPATHS.crn}`, 'CRN');
        this.registry = new Link(this.#codexPage,
            `${this.#locator}//dt[contains(text(), 'Registry:')]/following-sibling::dd`, 'Registry');
        this.documentDate = new Text(this.#codexPage,
            `${this.#locator}${CARD_FIELD_XPATHS.documentDate}`, 'Document Date');
        this.filingButton = new Button(this.#codexPage,
            `${this.#locator}//a[normalize-space(text())="Filing"]`,
            'Filing Button');
        this.csvButton = new Button(this.#codexPage,
            `${this.#locator}//a[normalize-space(text())="xBRL-CSV"]`,
            'xBRL-CSV Button');
        this.jsonButton = new Button(this.#codexPage,
            `${this.#locator}//a[normalize-space(text())="xBRL-JSON"]`,
            'xBRL-JSON Button');
        this.filingDate = new Text(this.#codexPage,
            `${this.#locator}${CARD_FIELD_XPATHS.filingDate}`, 'Date Filed');
        this.viewerButton = new Button(this.#codexPage,
            `${this.#locator}//a[normalize-space(text())="Open Viewer"]`, 'Viewer Button');
    }
}

import { Button, Dropdown, Link, Text, TextInput } from '../core_elements.js';
import { expect } from '@playwright/test';

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
        let result = null;
        await expect(async () => {
            const cards = await this.#getResultCards();
            for (const card of cards) {
                const [cardName, cardCrn,
                    cardDocDate, cardFilingDate] = await Promise.all([
                    await card.companyName.getText(),
                    await card.crn.getText(),
                    await card.documentDate.getText(),
                    await card.filingDate.getText()
                ]);

                if ((name === '' || cardName === name) &&
                    (crn === '' || cardCrn === crn) &&
                    (docDate === '' || cardDocDate === docDate) &&
                    (filingDate === '' || cardFilingDate === filingDate)) {
                    result = card;
                    break;
                }
            }
            if (result === null) {
                throw new Error(`No search result found with name: ${name}, doc date: ${docDate}, filing date: ${filingDate}`);
            }
        }).toPass({ timeout: 30000, intervals: [500] });
        return result;
    }

    get #resultCards() {
        return this.#codexPage.page.locator('xpath=//*[contains(@id,"result")]');
    }

    /**
     * Gets the search result cards from the search results.
     * @returns {Promise<SearchResultCard[]>} - for each search result.
     */
    async #getResultCards() {
        const elementIds = await this.#resultCards.evaluateAll(elements => elements.map(e => e.id));
        return elementIds.map(id => new SearchResultCard(this.#codexPage, `//*[@id="${id}"]`));
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
            `${this.#locator}//h3`, 'Company Name');
        this.crn = new Link(this.#codexPage,
            `${this.#locator}//dt[span[contains(text(), 'CRN')]]/following-sibling::dd`, 'CRN');
        this.registry = new Link(this.#codexPage,
            `${this.#locator}//dt[contains(text(), 'Registry:')]/following-sibling::dd`, 'Registry');
        this.documentDate = new Text(this.#codexPage,
            `${this.#locator}//dt[span[contains(text(), 'Document Date:')]]/following-sibling::dd`,
            'Document Date');
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
            `${this.#locator}//dt[span[contains(text(), 'Date Filed:')]]/following-sibling::dd`,
            'Date Filed');
        this.viewerButton = new Button(this.#codexPage,
            `${this.#locator}//a[normalize-space(text())="Open Viewer"]`, 'Viewer Button');
    }
}

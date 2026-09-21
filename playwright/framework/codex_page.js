import { expect } from '@playwright/test';
import { Search } from './page_objects/search.js';

export class CodexPage {
    logs = [];

    constructor(page) {
        this.page = page;
        this.search = new Search(this);

        page.on('console', msg => this.log(`${msg.type()} ${msg.text()}`))
            .on('pageerror', err => this.log(err.toString()))
            .on('response', response => this.log(`${response.status()} ${response.url()}`))
            .on('requestfailed', request => this.log(`${request.failure().errorText} ${request.url()}`));
    }

    log(message) {
        this.logs.push(message);
    }

    /**
     * Asserts page navigation by checking the page title.
     * @param {string} title - Text the page title is expected to contain.
     * @returns {Promise<void>}
     */
    async assertPageNavigation(title) {
        this.log(`Asserting page title contains "${title}"`);
        await expect(this.page).toHaveTitle(new RegExp(RegExp.escape(title)), { timeout: 90000 });
    }

    /**
     * Asserts the viewer has finished generating in the tab opened by Open Viewer.
     * The tab shows a loading page until processing completes, so this waits for
     * the generated viewer title rather than the loading title.
     * @param {import('@playwright/test').Page} viewerTab - The tab opened by Open Viewer.
     * @returns {Promise<void>}
     */
    async assertViewerLoaded(viewerTab) {
        this.log('Asserting the viewer loaded in the new tab');
        await expect(viewerTab).toHaveURL(/\/view\//);
        await expect(viewerTab).toHaveTitle('iXBRL Viewer', { timeout: 90000 });
    }

    /**
     * Navigates the browser to the UK iXBRL Viewer page.
     * @returns {Promise<void>}
     */
    async navigateToFilingIndex() {
        this.log('Navigating to /');
        await this.page.goto('/', { waitUntil: 'networkidle' });
        await expect(this.page.locator('xpath=//*[contains(@class, "loading")]')).toHaveCount(0);
    }
}

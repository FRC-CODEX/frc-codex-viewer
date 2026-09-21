import { CodexPage } from '../framework/codex_page';
import { afterEach, beforeEach, describe, test } from "@jest/globals";

describe('Filing Index', () => {
    let codexPage;

    beforeEach(async () => {
        codexPage = new CodexPage();
        await codexPage.buildPage();
    })

    afterEach(async () => {
        await codexPage.tearDown();
    })

    test('CRN Search', async () => {

        //'Navigate to Filing Index
        await codexPage.navigateToFilingIndex();

        // Search for company by CRN number
        await codexPage.search.companyNameAndNumberInput.enterText('11162569');
        await codexPage.search.submitButton.select();

        // Assert search results
        await codexPage.search.assertResultCount(2);
        const result = await codexPage.search.getSearchResult('TUSCANY PIZZA LTD',
            '11162569', '2023-01-31', '2023-06-02');
        await result.companyName.scrollToElement();
        await result.filingButton.scrollToElement();
        await result.filingButton.assertVisible();

        // Open Viewer
        await result.viewerButton.scrollToElement();
        await result.viewerButton.select();
        await codexPage.assertPageNavigation('iXBRL Viewer');

        // Go back to Filing Index
        await codexPage.page.goBack({waitUntil: 'domcontentloaded'});
        await codexPage.assertPageNavigation('UK iXBRL Viewer');
    });
});

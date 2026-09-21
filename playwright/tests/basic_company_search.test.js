import { test } from '../framework/fixtures.js';

test.describe('Filing Index', () => {
    test('Basic Company Search', async ({ codexPage }) => {
        await codexPage.navigateToFilingIndex();

        // Search for company by name only
        await codexPage.search.companyNameAndNumberInput.enterText('TUSCANY PIZZA LTD');
        await codexPage.search.submitButton.select();

        // Assert search results
        const result = await codexPage.search.getSearchResult('TUSCANY PIZZA LTD',
            '11162569', '2023-01-31', '2023-06-02');

        // Open filing
        await result.filingButton.scrollToElement();
        await result.filingButton.select();
        await codexPage.assertPageNavigation('TUSCANY PIZZA LTD');

        // Go back to Filing Index
        await codexPage.page.goBack({waitUntil: 'domcontentloaded'});
        await codexPage.assertPageNavigation('UK iXBRL Viewer');

        // Open Viewer
        await result.viewerButton.scrollToElement();
        const viewerTab = await result.viewerButton.selectPopup();
        await codexPage.assertViewerLoaded(viewerTab);
    });
});

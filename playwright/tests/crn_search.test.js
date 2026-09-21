import { test } from '../framework/fixtures.js';

test.describe('Filing Index', () => {
    test('CRN Search', async ({ codexPage }) => {

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
        const viewerTab = await result.viewerButton.selectPopup();
        await codexPage.assertViewerLoaded(viewerTab);

        // Go back to Filing Index
        await codexPage.page.goBack({waitUntil: 'domcontentloaded'});
        await codexPage.assertPageNavigation('UK iXBRL Viewer');
    });
});

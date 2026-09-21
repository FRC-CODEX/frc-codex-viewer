import { test } from '../framework/fixtures.js';

test.describe('Filing Index', () => {
    test('Advanced Search - Date Filed', async ({ codexPage }) => {
        await codexPage.navigateToFilingIndex();
        await codexPage.search.companyNameAndNumberInput.enterText('TUSCANY PIZZA LTD');

        // Set the minimum filing date
        await codexPage.search.advancedSearch.minFilingDateYear.scrollToElement();
        await codexPage.search.advancedSearch.minFilingDateYear.enterText('2023');
        await codexPage.search.advancedSearch.minFilingDateMonth.enterText('6');
        await codexPage.search.advancedSearch.minFilingDateDay.enterText('2');

        // Set the maximum filing date
        await codexPage.search.advancedSearch.maxFilingDateYear.enterText('2023');
        await codexPage.search.advancedSearch.maxFilingDateMonth.enterText('6');
        await codexPage.search.advancedSearch.maxFilingDateDay.enterText('2');

        await codexPage.search.submitButton.select();

        // Assert search results
        await codexPage.search.assertResultCount(1);
        const result = await codexPage.search.getSearchResult('TUSCANY PIZZA LTD',
            '11162569', '2023-01-31', '2023-06-02');
        await result.filingButton.scrollToElement();
        await result.filingButton.assertVisible();

        // Open Viewer
        await result.viewerButton.scrollToElement();
        await result.viewerButton.select();
        await codexPage.assertPageNavigation('iXBRL Viewer');

    });
});

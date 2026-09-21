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

    test('Registry Search', async () => {
        await codexPage.navigateToFilingIndex();

        // Search for company by name and registry
        await codexPage.search.companyNameAndNumberInput.enterText('TUSCANY PIZZA LTD');
        await codexPage.search.registry.scrollToElement();
        await codexPage.search.registry.selectOption('CH');
        await codexPage.search.registry.assertValue('CH');
        await codexPage.search.submitButton.select();

        // Assert search results
        const ch_result = await codexPage.search.getSearchResult(
            'TUSCANY PIZZA LTD', '11162569', '2023-01-31',
            '2023-06-02');
        await ch_result.filingButton.scrollToElement();
        await ch_result.filingButton.assertVisible();
        await ch_result.viewerButton.assertVisible();

        // Change Registry to FCA, then search again
        await codexPage.search.registry.scrollToElement();
        await codexPage.search.registry.selectOption('FCA');
        await codexPage.search.registry.assertValue('FCA');
        await codexPage.search.submitButton.select();

        // Assert search results return 0 results for this company
        await codexPage.search.assertResultCount(0);

        // Change Registry to Any, then search again
        await codexPage.search.registry.scrollToElement();
        await codexPage.search.registry.selectOption('');
        await codexPage.search.registry.assertValue('');
        await codexPage.search.submitButton.select();

        // Assert search results return results for this company
        await codexPage.search.getSearchResult('TUSCANY PIZZA LTD',
            '11162569', '2023-01-31', '2023-06-02');
        await ch_result.filingButton.scrollToElement();
        await ch_result.filingButton.assertVisible();
        await ch_result.viewerButton.assertVisible();
    });
});

import { test as base, expect } from '@playwright/test';
import { CodexPage } from './codex_page.js';

export const test = base.extend({
    codexPage: async ({ page }, use, testInfo) => {
        const codexPage = new CodexPage(page);
        try {
            await use(codexPage);
        } finally {
            await testInfo.attach('browser.log', {
                body: codexPage.logs.join('\n'),
                contentType: 'text/plain',
            });
        }
    },
});

export { expect };

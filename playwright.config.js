import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './playwright/tests',
    timeout: 120000,
    forbidOnly: !!process.env.CI,
    workers: process.env.CI ? 1 : undefined,
    outputDir: './playwright/artifacts/test-results',
    reporter: [
        ['list'],
        ['html', { outputFolder: './playwright/artifacts/report', open: 'never' }],
    ],
    use: {
        browserName: 'chromium',
        channel: 'chromium',
        baseURL: 'http://localhost:8080',
        viewport: { width: 1440, height: 821 },
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
    },
});

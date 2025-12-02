import fs from 'fs';
import { expect } from '@jest/globals';
import puppeteer from 'puppeteer-core';
import { PuppeteerScreenRecorder } from 'puppeteer-screen-recorder';
import { Search } from "./page_objects/search";
import { waitFor } from "./utils";

export class CodexPage {
    browser;
    page;
    search;

    #artifactDirectory = './puppeteer/artifacts';
    #cleanedTestName = expect.getState()
        .currentTestName
        .replaceAll(/[^a-zA-Z0-9-]/g, '_');
    #isCi = process.env.CI === 'true';
    #logMsgs = [];
    #recorder;

    /**
     * Asserts page navigation by checking the page title.
     * @param title
     * @returns {Promise<void>}
     */
    async assertPageNavigation(title) {
        await waitFor(async () => {
            const pageTitle = await this.page.title();
            expect(pageTitle).toContain(title);
        }, 90000, 500);
    }

    async buildPage() {
        // Launch the browser
        this.browser = await puppeteer.launch({
            channel: 'chrome',
            headless: this.#isCi ? 'new' : false,
            args: [`--window-size=1440,900`],
            defaultViewport: { width: 1440, height: 821 },
        });
        const pages = await this.browser.pages();
        this.page = pages[0];
        this.#recorder = new PuppeteerScreenRecorder(this.page);
        this.search = new Search(this);

        // Set up the video recording
        const videoDir = `${this.#artifactDirectory}/video`;
        const videoPath = `${videoDir}/${this.#cleanedTestName}.mp4`;
        this.#createDirectory(videoDir);
        await this.#recorder.start(videoPath);

        this.streamLogsToFile(
            `${this.#artifactDirectory}/${this.#cleanedTestName}_chrome_debug.log`);
    }

    log(message) {
        this.#logMsgs.push(message);
    }

    /**
     * Navigates the browser to the UK iXBRL Viewer page.
     * @returns {Promise<void>}
     */
    async navigateToFilingIndex() {
        const url = `http://localhost:8080`;
        this.log(`Navigating to ${url}`);
        await this.page.goto(url, { waitUntil: 'networkidle0' });
        await this.page.waitForSelector(
            'xpath/' + '//*[contains(@class, "loading")]',
            { visible: false, hidden: true });
    }

    /**
     * Outputs logs, stops video recording, and closes the browser
     * @returns {Promise<void>}
     */
    async tearDown() {
        console.log(this.#logMsgs.join('\n'));
        await this.#recorder.stop();
        await this.browser.close();
    }

    /**
     * Creates a directory if it does not exist.
     * @param {string} path
     * @returns {void}
     */
    #createDirectory(path) {
        if (!fs.existsSync(path)) {
            fs.mkdirSync(path, { recursive: true });
        }
    }

    /**
     * Streams browser logs to a file.
     * @param {string} filename
     * @returns {void}
     */
    streamLogsToFile(filename) {
        const append = (content) => fs.appendFile(filename, `${content}\n`,
            function(err) {
                if (err) throw err;
            });

        // Delete the file if it already exists
        fs.rmSync(filename, { force: true });

        // Pipe the log messages to the file
        this.page.on('console', msg => append(
            `${msg.type().substring(0, 3).toUpperCase()} ${msg.text()}`))
            .on('pageerror', function(err) {
                let value = err.toString();
                append(value);
            })
            .on('response', response => append(
                `${response.status()} ${response.url()}`))
            .on('requestfailed', request => append(
                `${request.failure().errorText} ${request.url()}`));
    }

    async waitMilliseconds(milliseconds) {
        return new Promise(function(resolve) {
            setTimeout(resolve, milliseconds);
        });
    }
}

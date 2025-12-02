import { ElementHandle } from 'puppeteer-core';
/**
 * Gets an element matching an XPath selector.
 *
 * @param {Page} page - The Puppeteer page to search for the element on.
 * @param {string} xpath - The XPath selector to search for.
 * @returns {Promise<import('puppeteer').ElementHandle>} - Resolves with the element matching the XPath selector.
 */
 export async function getElementByXpath(page, xpath) {
    const xpathSelector = `::-p-xpath(${xpath})`;
    return await page.waitForSelector(xpathSelector);
}

/**
 * Gets all elements matching an XPath selector.
 * @param {Page} page - The puppeteer page to search for elements on.
 * @param {string} xpath - The XPath selector to search for.
 * @returns {Promise<import('puppeteer').ElementHandle>} - Resolves with an array of elements matching the XPath selector.
 */
export async function getElementsByXpath(page, xpath) {
    const xpathSelector = `::-p-xpath(${xpath})`;
    return await page.$$(xpathSelector);
}

/**
 * Gets the value of a property of an element.
 * @param {ElementHandle} elementHandle - The element to get the property of.
 * @param {string} propertyName - The name of the property to get.
 * @returns {Promise<*>} - Resolves with the value of the property.
 */
export async function getProperty(elementHandle, propertyName) {
    return (await elementHandle.getProperty(propertyName)).jsonValue();
}

/**
 * Gets the text content of an element.
 * @param {ElementHandle} elementHandle - The element to get the text content of.
 * @returns {Promise<string>} - Resolves with the text content of the element.
 */
export async function getTextContent(elementHandle) {
    return getProperty(elementHandle, 'textContent');
}

/**
 * Repeatedly calls a given function until it either succeeds or a timeout is reached.
 * @param {Function} fn - The function to be called repeatedly.
 * @param {number} timeout - The maximum time to wait in milliseconds.
 * @param {number} interval - The time to wait between attempts in milliseconds.
 * @returns {Promise<*>} - Resolves with the result of the function if it succeeds, otherwise rejects with the last error.
 */
export async function waitFor(fn, timeout = 5000, interval = 100) {
    const endTime = Date.now() + timeout;
    let lastError;

    while (Date.now() < endTime) {
        try {
            return await fn();
        } catch (error) {
            lastError = error;
            if (Date.now() >= endTime) {
                throw lastError;
            }
            await new Promise(resolve => setTimeout(resolve, interval));
        }
    }
    throw lastError;
}

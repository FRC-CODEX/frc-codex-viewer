module.exports = {
    input: [
        "src/main/resources/templates/**/*.html",
    ],
    output: "src/main/resources/i18n/$LOCALE/$NAMESPACE.json",
    locales: [ "cy", "en", "es" ],
    sort: true
};

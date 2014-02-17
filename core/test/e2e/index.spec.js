describe("beaker", function () {
    describe("index page", function () {
        it("should display the correct title", function () {
            browser.get('beaker/#')
            expect(browser.getTitle()).toBe('Beaker');
        });
    });
});
describe("beaker", function () {
    describe("index page", function () {
        it("should display the correct title", function () {
            browser.get('beaker/#')
            expect(browser.getTitle()).toBe('Beaker');
        });
    });

    describe("new notebook", function () {
        it("should display the correct title", function () {
            browser.get('beaker/#/session/new')
            expect(browser.getTitle()).toMatch('New Notebook');
            var codeCell = element(by.css('bk-notebook'))
                .element(by.repeater('cell in getChildren()').row(0)) // get the H1 cell
                .element(by.repeater('cell in getChildren()').row(0)) // get the code cell
            var inputCell = codeCell.element(by.model("cellmodel.input.body"));
            //var inputMenu = codeCell.element(by.css('input-menu')).element(by.css('ul')).element(by.css('a'));
            //expect(inputMenu.getText()).toMatch('Run');

            browser.executeScript('ccm.setValue("102 + 1");');
            expect(inputCell.getAttribute('value')).toEqual('102 + 1');
            browser.executeScript('bkHelper.evaluate("code001")');
            browser.sleep(3000);

            var output = codeCell.element(by.css('bk-code-cell-output'));
            expect(output.getText()).toMatch('103');
        });
    });

});
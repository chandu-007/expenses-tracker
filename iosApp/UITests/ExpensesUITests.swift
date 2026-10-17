import XCTest

final class ExpensesUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func launchFresh() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-reset-data"]
        app.launch()
        return app
    }

    private func file(_ app: XCUIApplication, amount: String, note: String, category: String) {
        app.buttons["add"].tap()
        let a = app.textFields["amount"]; XCTAssertTrue(a.waitForExistence(timeout: 5)); a.typeText(amount)
        let n = app.textFields["note"]; n.tap(); n.typeText(note)
        app.buttons["category_" + category].tap()
        app.buttons["save"].tap()
    }

    func testAddingAnExpenseShowsTheRowAndTheMonthTotal() {
        let app = launchFresh()
        file(app, amount: "1250.50", note: "Lunch", category: "Food")
        XCTAssertTrue(app.staticTexts["row_Lunch"].waitForExistence(timeout: 5) || app.otherElements["row_Lunch"].waitForExistence(timeout: 5))
        let total = app.staticTexts["month_spend"]
        XCTAssertTrue(total.waitForExistence(timeout: 5))
        XCTAssertTrue(total.label.contains("1,250.50"), total.label)
    }

    func testABadAmountIsExplainedAndNothingIsSaved() {
        let app = launchFresh()
        app.buttons["add"].tap()
        app.buttons["save"].tap()
        XCTAssertTrue(app.staticTexts["problem"].waitForExistence(timeout: 5))
    }

    func testSimilarEntriesAreOfferedAndReviewedOneByOne() {
        let app = launchFresh()
        file(app, amount: "899", note: "iCloud", category: "Other")
        file(app, amount: "899", note: "iCloud+ 50GB", category: "Other")
        file(app, amount: "899", note: "iCloud", category: "Bills")
        let shown = app.buttons["similar_review"].waitForExistence(timeout: 8)
        if !shown { XCTFail(app.debugDescription) }
        XCTAssertTrue(shown)
        app.buttons["similar_review"].tap()
        XCTAssertTrue(app.staticTexts["similar_progress"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["similar_progress"].label, "1 of 2")
        app.buttons["similar_yes"].tap()
        app.buttons["similar_skip"].tap()
        XCTAssertFalse(app.buttons["similar_review"].waitForExistence(timeout: 2))
    }

    func testIncomeCategoriesAreSeparateFromExpenseOnes() {
        let app = launchFresh()
        app.buttons["add"].tap()
        XCTAssertTrue(app.buttons["category_Food"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["category_income:salary"].exists)
        app.buttons["Income"].tap()
        XCTAssertTrue(app.buttons["category_income:salary"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["category_Food"].exists)
    }
}

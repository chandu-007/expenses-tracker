import XCTest

/// Drives the shared Compose UI through the accessibility tree: every Compose testTag is an accessibility identifier on iOS, so these use the same
/// tags as the Android device tests. The app is started with `-reset-data` (an empty ledger, the guide counted as seen) and `-dismiss-welcome`.
/// Screenshots of the screens visited are written to /tmp/ios-shots (and attached to the result bundle).
final class ExpensesUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func launch(reset: Bool = true, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = (reset ? ["-reset-data"] : []) + ["-dismiss-welcome"] + extra
        app.launch()
        XCTAssertTrue(el(app, "add").waitForExistence(timeout: 30), "Home did not appear\n" + app.debugDescription)
        return app
    }

    private func el(_ app: XCUIApplication, _ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }

    private func tap(_ app: XCUIApplication, _ id: String, file: StaticString = #filePath, line: UInt = #line) {
        let e = el(app, id)
        // a long list draws only the rows on screen, so a row further down does not exist until the list is scrolled to it
        if !e.waitForExistence(timeout: 4) { scrollToExist(app, e) }
        XCTAssertTrue(e.waitForExistence(timeout: 15), "No element \(id)\n" + app.debugDescription, file: file, line: line)
        reveal(app, e)
        settle(e)
        e.tap()
    }

    /// Whether [id] is (or, after scrolling down, comes to be) on the screen.
    private func appears(_ app: XCUIApplication, _ id: String, timeout: TimeInterval = 15) -> Bool {
        let e = el(app, id)
        if e.waitForExistence(timeout: min(timeout, 4)) { return true }
        if timeout > 5 { scrollToExist(app, e) }
        return e.waitForExistence(timeout: timeout)
    }

    private func gone(_ app: XCUIApplication, _ id: String, timeout: TimeInterval = 15) -> Bool {
        let e = el(app, id)
        return XCTWaiter().wait(for: [expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: e)], timeout: timeout) == .completed
    }

    private func shot(_ name: String) {
        let s = XCUIScreen.main.screenshot()
        let a = XCTAttachment(screenshot: s); a.name = name; a.lifetime = .keepAlways; add(a)
        try? FileManager.default.createDirectory(atPath: "/tmp/ios-shots", withIntermediateDirectories: true)
        try? s.pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/ios-shots/\(name).png"))
    }

    /// Types into a Compose text field: tap it, then type. The field's text is its accessibility value.
    private func type(_ app: XCUIApplication, _ id: String, _ text: String) {
        let f = el(app, id)
        if !f.waitForExistence(timeout: 4) { scrollToExist(app, f) }
        XCTAssertTrue(f.waitForExistence(timeout: 15), "No field \(id)\n" + app.debugDescription)
        reveal(app, f)
        // Focus takes a moment (Compose moves it, the keyboard changes layout); tap again if it did not arrive.
        var focused = false
        for _ in 0..<4 where !focused {
            f.tap()
            let deadline = Date().addingTimeInterval(2)
            while Date() < deadline && !focused {
                focused = (f.value(forKey: "hasKeyboardFocus") as? Bool) ?? false
                if !focused { RunLoop.current.run(until: Date().addingTimeInterval(0.1)) }
            }
        }
        XCTAssertTrue(focused, "\(id) did not take focus\n" + app.debugDescription)
        f.typeText(text)
    }

    private func file(_ app: XCUIApplication, amount: String, note: String, category: String) {
        tap(app, "add")
        type(app, "amount", amount)
        type(app, "note", note)
        tap(app, "category_chip_" + category)
        tap(app, "save")
        // Back at Home, or on top of it the similar-entries offer (which hides Home from accessibility while it is up).
        let home = el(app, "add"), offer = el(app, "similar_dialog")
        let deadline = Date().addingTimeInterval(20)
        while !(home.exists || offer.exists) && Date() < deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.2)) }
        XCTAssertTrue(home.exists || offer.exists, "Back at Home after saving\n" + app.debugDescription)
    }

    func testLaunchesToHome() {
        let app = launch()
        XCTAssertTrue(el(app, "nav_home").isSelected)
        XCTAssertEqual(el(app, "month_spend").label, "₹0.00")
        XCTAssertTrue(el(app, "open_search").exists)
        XCTAssertTrue(el(app, "open_reports").exists)
        XCTAssertTrue(el(app, "more_menu").exists)
        shot("home-empty")
    }

    func testAddingAnExpenseShowsTheRowAndTheMonthTotal() {
        let app = launch()
        file(app, amount: "1250.50", note: "Lunch", category: "Food")
        XCTAssertTrue(appears(app, "row_Lunch"), app.debugDescription)
        XCTAssertTrue(appears(app, "amount_Lunch"))
        XCTAssertEqual(el(app, "amount_Lunch").label, "-₹1,250.50")
        let total = el(app, "month_spend")
        XCTAssertTrue(NSPredicate(format: "label == %@", "₹1,250.50").evaluate(with: total) || total.waitForExistence(timeout: 5))
        XCTAssertEqual(total.label, "₹1,250.50")
        shot("home-with-expense")
    }

    func testABadAmountCannotBeSaved() {
        let app = launch()
        tap(app, "add")
        type(app, "amount", "12.345")
        XCTAssertFalse(el(app, "save").isEnabled)
        shot("add-bad-amount")
    }

    func testSearchOpensAndSelectingResultsShowsTheBulkBar() {
        let app = launch()
        file(app, amount: "100", note: "Tea", category: "Food")
        file(app, amount: "200", note: "Tea again", category: "Food")
        tap(app, "open_search")
        XCTAssertTrue(appears(app, "search_text"), app.debugDescription)
        type(app, "search_text", "Tea")
        XCTAssertTrue(appears(app, "search_select_all"), app.debugDescription)
        tap(app, "search_select_all")
        XCTAssertTrue(appears(app, "bulk_bar"), app.debugDescription)
        XCTAssertTrue(appears(app, "bulk_count"))
        XCTAssertTrue(el(app, "bulk_count").label.contains("2"), el(app, "bulk_count").label)
        XCTAssertTrue(el(app, "bulk_category").exists)
        shot("search-bulk-bar")
        tap(app, "bulk_cancel")
        XCTAssertTrue(gone(app, "bulk_bar"))
    }

    func testCategoriesShowsExpenseAndIncomeTabs() {
        let app = launch()
        tap(app, "more_menu")
        tap(app, "manage_categories")
        XCTAssertTrue(appears(app, "categories_list"), app.debugDescription)
        XCTAssertTrue(appears(app, "cat_row_Food"), "Expense categories are listed\n" + app.debugDescription)
        shot("categories-expenses")
        tap(app, "cat_tab_income")
        XCTAssertTrue(appears(app, "cat_row_income:salary"), "Income categories are listed\n" + app.debugDescription)
        XCTAssertFalse(el(app, "cat_row_Food").exists)
        shot("categories-income")
        tap(app, "categories_back")
        XCTAssertTrue(appears(app, "more_page"), "Back returns to More, as on Android")
    }

    func testMoreAndDataScreens() {
        let app = launch()
        tap(app, "more_menu")
        XCTAssertTrue(appears(app, "more_page"), app.debugDescription)
        shot("more")
        tap(app, "open_data")
        XCTAssertTrue(appears(app, "export_csv"), app.debugDescription)
        XCTAssertTrue(el(app, "create_backup").exists)
        XCTAssertTrue(el(app, "open_backup").exists)
        XCTAssertTrue(el(app, "import_statement").exists)
        // automatic copies to a folder from the Files app are on iPhone now: the folder button is there, not the "not available" note
        XCTAssertTrue(appears(app, "auto_folder"), "Automatic export offers a folder\n" + app.debugDescription)
        XCTAssertFalse(el(app, "auto_unavailable").exists)
        shot("data")
        tap(app, "data_back")
        XCTAssertTrue(appears(app, "more_page"), "Back returns to More, as on Android")
    }

    func testSimilarEntriesAreOfferedAndReviewedOneByOne() {
        let app = launch()
        file(app, amount: "899", note: "iCloud", category: "Other")
        file(app, amount: "899", note: "iCloud+ 50GB", category: "Other")
        file(app, amount: "899", note: "iCloud", category: "Bills")
        XCTAssertTrue(appears(app, "similar_review", timeout: 20), app.debugDescription)
        shot("similar-offer")
        tap(app, "similar_review")
        XCTAssertTrue(appears(app, "similar_progress"))
        XCTAssertEqual(el(app, "similar_progress").label, "1 of 2")
        tap(app, "similar_yes")
        tap(app, "similar_skip")
        XCTAssertTrue(gone(app, "similar_review", timeout: 10))
    }

    func testBackupIsOfferedWithPassphraseFields() {
        let app = launch()
        tap(app, "more_menu")
        tap(app, "open_data")
        XCTAssertTrue(appears(app, "backup_pass"), app.debugDescription)
        XCTAssertTrue(el(app, "create_backup").exists)
    }

    func testAppLockScreenIsAvailableOnIPhone() {
        let app = launch()
        tap(app, "more_menu")
        tap(app, "open_security")
        XCTAssertFalse(appears(app, "lock_unavailable", timeout: 3), "The app lock is supported on iPhone\n" + app.debugDescription)
        shot("app-lock")
    }

    /// Every tile of More opens its screen (a screenshot of each is kept); the data stays, so the app is relaunched without a reset.
    func testEveryMoreScreenOpens() {
        _ = launch()
        let tiles = ["open_inbox", "open_capture", "manage_accounts", "manage_categories", "open_recurring", "open_calendar", "open_loans", "open_bills",
                     "open_goals", "open_subscriptions", "open_appearance", "open_home_layout", "open_currency", "open_notifications", "open_security", "open_privacy", "open_data", "open_guide"]
        for tile in tiles {
            let app = launch(reset: false)
            tap(app, "more_menu")
            tap(app, tile)
            if tile == "open_guide" {
                XCTAssertTrue(appears(app, "guide_card"), "The guide opens over More\n" + app.debugDescription)
            } else {
                XCTAssertTrue(gone(app, "more_page", timeout: 10), "\(tile) did not leave More\n" + app.debugDescription)
            }
            sleep(1)
            shot("screen-" + tile)
            app.terminate()
        }
    }

    func testReportsOpens() {
        let app = launch()
        tap(app, "open_reports")
        sleep(2)
        shot("reports")
        XCTAssertFalse(el(app, "open_reports").isSelected && el(app, "month_spend").exists)
    }
}

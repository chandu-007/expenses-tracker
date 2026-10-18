import XCTest

/// Bank-message capture on iPhone, through the screens. The messages (made up) are handed to the app at launch with `-ingest-message "sender|words"`, which
/// calls the same Kotlin entry the Shortcuts intent calls (`IosCapture`), so what is checked here is what an automation would leave: a pending row in To review,
/// nothing for an OTP or a person's text, one item for the same text twice. Pasting and the setup guide are driven by hand. Screenshots go to /tmp/ios-shots.
final class CaptureUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private let hdfc = "AX-HDFCBK-S"
    private let debit = "Rs.450.00 debited from a/c XX1234 on 18-10-26 to TEST SHOP. Ref 123456789012. Not you? Call 1800 000 000 -HDFC Bank"
    private let otp = "482913 is your OTP for a payment of Rs.450.00 at TEST SHOP. Do not share it. -HDFC Bank"
    private let person = "Rs.300.00 debited from a/c XX1234 for the rent I owe you, 987654321098"

    private func launch(_ messages: [(String, String)] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-reset-data", "-dismiss-welcome"] + messages.flatMap { ["-ingest-message", "\($0.0)|\($0.1)"] }
        app.launch()
        XCTAssertTrue(el(app, "add").waitForExistence(timeout: 30), "Home did not appear\n" + app.debugDescription)
        return app
    }

    private func el(_ app: XCUIApplication, _ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }
    private func appears(_ app: XCUIApplication, _ id: String, timeout: TimeInterval = 15) -> Bool { el(app, id).waitForExistence(timeout: timeout) }

    private func gone(_ app: XCUIApplication, _ id: String, timeout: TimeInterval = 15) -> Bool {
        let e = el(app, id)
        return XCTWaiter().wait(for: [expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: e)], timeout: timeout) == .completed
    }

    private func reveal(_ app: XCUIApplication, _ e: XCUIElement) {
        for _ in 0..<25 {
            let keyboard = app.keyboards.firstMatch
            let limit = keyboard.exists ? keyboard.frame.minY : app.frame.maxY
            if e.frame.minY >= 100 && e.frame.maxY <= limit - 4 && e.isHittable { return }
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: min(0.55, (limit - 20) / app.frame.height)))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
            from.press(forDuration: 0.05, thenDragTo: to)
            sleep(1)
        }
    }

    private func tap(_ app: XCUIApplication, _ id: String, file: StaticString = #filePath, line: UInt = #line) {
        let e = el(app, id)
        XCTAssertTrue(e.waitForExistence(timeout: 15), "No element \(id)\n" + app.debugDescription, file: file, line: line)
        reveal(app, e)
        e.tap()
    }

    /// Taps [id] until [next] is on the screen: a tap on a list that is still settling from a scroll only stops the scroll.
    private func go(_ app: XCUIApplication, _ id: String, expecting next: String, file: StaticString = #filePath, line: UInt = #line) {
        for _ in 0..<4 {
            let e = el(app, id)
            XCTAssertTrue(e.waitForExistence(timeout: 15), "No element \(id)\n" + app.debugDescription, file: file, line: line)
            reveal(app, e)
            sleep(1)
            e.tap()
            if appears(app, next, timeout: 6) { return }
        }
        XCTFail("\(next) did not appear after tapping \(id)\n" + app.debugDescription, file: file, line: line)
    }

    private func type(_ app: XCUIApplication, _ id: String, _ text: String) {
        let f = el(app, id)
        XCTAssertTrue(f.waitForExistence(timeout: 15), "No field \(id)\n" + app.debugDescription)
        reveal(app, f)
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

    private func shot(_ name: String) {
        let s = XCUIScreen.main.screenshot()
        let a = XCTAttachment(screenshot: s); a.name = name; a.lifetime = .keepAlways; add(a)
        try? FileManager.default.createDirectory(atPath: "/tmp/ios-shots", withIntermediateDirectories: true)
        try? s.pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/ios-shots/\(name).png"))
    }

    private func openInbox(_ app: XCUIApplication) {
        tap(app, "more_menu")
        tap(app, "open_inbox")
        XCTAssertTrue(appears(app, "inbox_paste"), "To review shows the paste button on iPhone\n" + app.debugDescription)
    }

    private func count(_ app: XCUIApplication, _ outcome: String) -> String { el(app, "health_count_\(outcome)").label }

    private func openHealth(_ app: XCUIApplication) {
        tap(app, "more_menu")
        tap(app, "open_capture")
        go(app, "open_health", expecting: "health_headline")
    }

    /// No label or value anywhere on the screen carries [words]: used to check that what was ignored left no trace.
    private func assertNothingShows(_ app: XCUIApplication, _ words: [String]) {
        for w in words {
            let hits = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@ OR value CONTAINS %@", w, w)).count
            XCTAssertEqual(hits, 0, "\"\(w)\" is on the screen\n" + app.debugDescription)
        }
    }

    // (a) a bank text handed over by the intent's entry waits in To review
    func testABankTextFromTheShortcutWaitsInToReview() {
        let app = launch([(hdfc, debit)])
        openInbox(app)
        XCTAssertTrue(appears(app, "inbox_count"), app.debugDescription)
        XCTAssertEqual(el(app, "inbox_count").label, "1 to review")
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "450.00")).count > 0, "the amount is on the row\n" + app.debugDescription)
        shot("capture-to-review")
        // and it is nowhere near the ledger until the person adds it
        tap(app, "inbox_back")
        XCTAssertEqual(el(app, "month_spend").label, "₹0.00")
    }

    // (b) an OTP and a text from a person are ignored and leave only counts
    func testAnOtpAndAPersonalTextAreIgnoredAndLeaveNoWords() {
        let app = launch([(hdfc, otp), ("+919800000000", person)])
        openInbox(app)
        XCTAssertTrue(appears(app, "inbox_empty"), "Nothing waits in To review\n" + app.debugDescription)
        assertNothingShows(app, ["482913", "987654321098", "rent"])
        tap(app, "inbox_back")
        openHealth(app)
        XCTAssertEqual(count(app, "One-time passwords ignored"), "1")
        XCTAssertEqual(count(app, "Texts from people ignored"), "1")
        XCTAssertEqual(count(app, "Found as transactions"), "0")
        assertNothingShows(app, ["482913", "987654321098", "rent"])
        shot("capture-health-ignored")
    }

    // (c) the same text twice is one item
    func testTheSameTextTwiceMakesOneItem() {
        let app = launch([(hdfc, debit), (hdfc, debit)])
        openInbox(app)
        XCTAssertTrue(appears(app, "inbox_count"), app.debugDescription)
        XCTAssertEqual(el(app, "inbox_count").label, "1 to review")
        tap(app, "inbox_back")
        openHealth(app)
        XCTAssertEqual(count(app, "Found as transactions"), "1")
        XCTAssertEqual(count(app, "Same text seen again"), "1")
    }

    // (d) pasting: a bank message is captured, an ordinary text is not
    func testPastingABankMessageAndAnOrdinaryText() {
        let app = launch()
        openInbox(app)
        XCTAssertTrue(appears(app, "inbox_empty"))
        tap(app, "inbox_paste_type")
        type(app, "inbox_paste_field", "Rs.250.00 debited from a/c XX4321 on 18-10-26 to PASTE SHOP. Ref 555566667777. -HDFC Bank")
        tap(app, "inbox_paste_add")
        XCTAssertTrue(appears(app, "inbox_paste_result"), app.debugDescription)
        XCTAssertTrue(el(app, "inbox_paste_result").label.hasPrefix("Captured, review it in Expenses"), el(app, "inbox_paste_result").label)
        XCTAssertTrue(appears(app, "inbox_count"), app.debugDescription)
        XCTAssertEqual(el(app, "inbox_count").label, "1 to review")
        shot("capture-pasted")
        type(app, "inbox_paste_field", "See you at five, I will bring the tickets")
        tap(app, "inbox_paste_add")
        let result = el(app, "inbox_paste_result")
        let deadline = Date().addingTimeInterval(10)
        while !result.label.hasPrefix("Ignored") && Date() < deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.2)) }
        XCTAssertEqual(result.label, "Ignored: not a bank transaction")
        XCTAssertEqual(el(app, "inbox_count").label, "1 to review")
    }

    // (e) the Capture from texts screen is the Shortcuts setup guide on iPhone, and has none of the Android switches
    func testTheSetupGuideShowsOnIPhone() {
        let app = launch()
        tap(app, "more_menu")
        tap(app, "open_capture")
        XCTAssertTrue(appears(app, "shortcuts_title"), app.debugDescription)
        XCTAssertTrue(el(app, "shortcuts_steps").exists)
        XCTAssertTrue(el(app, "shortcuts_open").exists)
        XCTAssertFalse(el(app, "capture_sms_switch").exists)
        XCTAssertFalse(el(app, "capture_notif_switch").exists)
        XCTAssertTrue(el(app, "shortcuts_step_5").label.contains("Message Contains"), el(app, "shortcuts_step_5").label)
        XCTAssertTrue(el(app, "shortcuts_step_2").label.contains("Capture bank message"), el(app, "shortcuts_step_5").label)
        shot("capture-setup-top")
        reveal(app, el(app, "shortcuts_limits"))
        XCTAssertTrue(el(app, "shortcuts_privacy").exists)
        XCTAssertTrue(el(app, "shortcuts_limits").label.contains("Unknown Senders"))
        shot("capture-setup-limits")
    }
}

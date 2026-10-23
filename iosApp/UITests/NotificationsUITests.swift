import XCTest

/// Reminders: turning them on asks the system for permission (the dialog is accepted here), and the check, run through the debug launch
/// argument `-notify-debug-run`, hands the system a notification for a made-up bill due today and a pending one for a made-up bill due in two
/// days. What the system holds is read back by the app and shown as the accessibility label `notify_debug` (see Notifications.swift).
/// Background refresh timing cannot be tested on the simulator.
final class NotificationsUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func el(_ app: XCUIApplication, _ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }

    private func tap(_ app: XCUIApplication, _ id: String) {
        let e = el(app, id)
        if !e.waitForExistence(timeout: 4) { scrollToExist(app, e) }
        XCTAssertTrue(e.waitForExistence(timeout: 20), "No element \(id)\n" + app.debugDescription)
        reveal(app, e)
        settle(e)
        e.tap()
    }

    private func launch(reset: Bool, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = (reset ? ["-reset-data"] : []) + ["-dismiss-welcome"] + extra
        app.launch()
        XCTAssertTrue(el(app, "add").waitForExistence(timeout: 30), "Home did not appear\n" + app.debugDescription)
        return app
    }

    func testPermissionThenCheckHandsNotificationsToTheSystem() {
        // The system's permission dialog, whenever it comes up while the app is being driven.
        let monitor = addUIInterruptionMonitor(withDescription: "Notification permission") { alert in
            for name in ["Allow", "OK"] where alert.buttons[name].exists { alert.buttons[name].tap(); return true }
            return false
        }
        defer { removeUIInterruptionMonitor(monitor) }

        // 1. Notifications screen: blocked until the person allows; the Allow button asks the system.
        var app = launch(reset: true)
        tap(app, "more_menu")
        tap(app, "open_notifications")
        XCTAssertFalse(el(app, "notif_unavailable").exists, "Notifications must be available on iPhone")
        if el(app, "notif_blocked").waitForExistence(timeout: 10) {
            tap(app, "notif_allow")
            // The dialog belongs to SpringBoard; accept it directly as well as through the monitor.
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let allow = springboard.alerts.buttons["Allow"]
            let promptSeen = allow.waitForExistence(timeout: 10)
            if promptSeen { allow.tap() }
            try? FileManager.default.createDirectory(atPath: "/tmp/ios-shots", withIntermediateDirectories: true)
            try? "permission dialog shown: \(promptSeen)".write(toFile: "/tmp/ios-shots/notify-permission.txt", atomically: true, encoding: .utf8)
            app.tap() // lets the interruption monitor run if the dialog was still up
            let gone = XCTWaiter().wait(for: [expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: el(app, "notif_blocked"))], timeout: 15)
            XCTAssertEqual(gone, .completed, "The screen still says notifications are off after allowing\n" + app.debugDescription)
        }
        XCTAssertTrue(el(app, "notif_bills").waitForExistence(timeout: 10))
        app.terminate()

        // 2. The check, on the data kept from the first run.
        app = launch(reset: false, extra: ["-notify-debug-run"])
        let report = el(app, "notify_debug")
        XCTAssertTrue(report.waitForExistence(timeout: 40), "No report from the check\n" + app.debugDescription)
        let text = report.label
        try? FileManager.default.createDirectory(atPath: "/tmp/ios-shots", withIntermediateDirectories: true)
        try? text.write(toFile: "/tmp/ios-shots/notify-debug.txt", atomically: true, encoding: .utf8)
        XCTAssertTrue(text.contains("allowed=true"), text)
        XCTAssertFalse(text.contains("shown=0"), "Nothing was shown: " + text)
        // due today: a notification went out at once; due in two days: one is waiting in the system, set for the reminder hour
        XCTAssertTrue(text.contains("delivered=[bill:notify-debug-due:"), text)
        XCTAssertTrue(text.contains("pending=[bill:notify-debug-soon:"), text)
        app.terminate()
    }
}

import UIKit
import XCTest

/// "Read items from a photo" on the Add form, with a picture drawn here (an invented restaurant bill) handed over by the `-pick-file` launch argument.
final class BillPhotoUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func el(_ app: XCUIApplication, _ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }

    private func shot(_ name: String) {
        let s = XCUIScreen.main.screenshot()
        let a = XCTAttachment(screenshot: s); a.name = name; a.lifetime = .keepAlways; add(a)
        try? FileManager.default.createDirectory(atPath: "/tmp/ios-shots", withIntermediateDirectories: true)
        try? s.pngRepresentation.write(to: URL(fileURLWithPath: "/tmp/ios-shots/\(name).png"))
    }

    private func billPicture(path: String) {
        let lines = [
            "SUNRISE CAFE", "Bill No: 4411  Date: 01/10/26 20:50", "Item        Qty   Price   Amount",
            "Veg Biryani   1   180.00   180.00", "Masala Dosa   2   60.00   120.00", "Filter Coffee   1   40.00   40.00",
            "Sub Total   340.00", "CGST 2.5%   8.50", "SGST 2.5%   8.50", "Grand Total   357.00", "Thank you visit again",
        ]
        let size = CGSize(width: 1100, height: 900)
        let data = UIGraphicsImageRenderer(size: size).pngData { ctx in
            UIColor.white.setFill(); ctx.fill(CGRect(origin: .zero, size: size))
            let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.monospacedSystemFont(ofSize: 38, weight: .regular), .foregroundColor: UIColor.black]
            for (i, l) in lines.enumerated() { (l as NSString).draw(at: CGPoint(x: 30, y: 24 + CGFloat(i) * 78), withAttributes: attrs) }
        }
        try? data.write(to: URL(fileURLWithPath: path))
    }

    /// Opens the Add form and reads the drawn bill the way a person does: Read items from a photo, then Take a photo (the `-pick-file` picture stands in for the camera).
    private func readTheBill() -> XCUIApplication {
        let path = "/tmp/ios-billphoto.png"
        billPicture(path: path)
        let app = XCUIApplication()
        app.launchArguments = ["-reset-data", "-dismiss-welcome", "-pick-file", path]
        app.launch()
        XCTAssertTrue(el(app, "add").waitForExistence(timeout: 30))
        el(app, "add").tap()
        let read = el(app, "bill_items_read")
        scrollToExist(app, read)
        reveal(app, read)
        settle(read)
        read.tap()
        let camera = el(app, "bill_items_camera")
        XCTAssertTrue(camera.waitForExistence(timeout: 10), "the two ways to get the photo did not show\n" + app.debugDescription)
        reveal(app, camera); settle(camera)
        camera.tap()
        XCTAssertTrue(el(app, "bill_items_list").waitForExistence(timeout: 60), "the items did not appear\n" + app.debugDescription)
        return app
    }

    func testTheItemsStayUpUntilChosenAndAnotherPhotoCanBeReadAfterwards() {
        let app = readTheBill()
        let list = el(app, "bill_items_list")
        shot("bill-items-shown")
        // it must stay: look again after a few seconds
        sleep(6)
        XCTAssertTrue(list.exists, "the list of items went away by itself\n" + app.debugDescription)
        shot("bill-items-after-6s")
        XCTAssertTrue(el(app, "bill_items_apply").exists)
        let close = el(app, "bill_items_close")
        XCTAssertTrue(close.waitForExistence(timeout: 10))
        settle(close)
        close.tap()
        // and the button works again for another photo
        let read = el(app, "bill_items_read")
        XCTAssertTrue(read.waitForExistence(timeout: 10))
        XCTAssertTrue(read.isEnabled, "the button stayed disabled after a photo was read")
        reveal(app, read); settle(read)
        read.tap()
        let again = el(app, "bill_items_camera")
        XCTAssertTrue(again.waitForExistence(timeout: 10), "a second photo cannot be started\n" + app.debugDescription)
        reveal(app, again); settle(again)
        again.tap()
        XCTAssertTrue(el(app, "bill_items_list").waitForExistence(timeout: 60), "the second reading did not show its items\n" + app.debugDescription)
        shot("bill-items-second")
    }

    func testEachItemCanBeSavedAsAnEntryOfItsOwn() {
        let app = readTheBill()
        let each = el(app, "bill_items_mode_each")
        XCTAssertTrue(each.waitForExistence(timeout: 10), "the choice of one entry or each item did not show\n" + app.debugDescription)
        settle(each)
        each.tap()
        shot("bill-items-each")
        let apply = el(app, "bill_items_apply")
        settle(apply)
        apply.tap()
        // back at Home: a row for each item the picture was read into
        XCTAssertTrue(el(app, "add").waitForExistence(timeout: 30), app.debugDescription)
        let rows = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'row_'"))
        let deadline = Date().addingTimeInterval(30)
        while rows.count < 3 && Date() < deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.3)) }
        XCTAssertGreaterThanOrEqual(rows.count, 3, "an entry for each item was expected\n" + app.debugDescription)
        shot("bill-items-each-saved")
    }
}

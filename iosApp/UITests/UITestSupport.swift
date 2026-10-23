import XCTest

/// Scrolling that every UI test shares. A long screen (More, Your data, the Add form) draws only the rows on screen, so a row further down does not exist
/// until the list is scrolled to it, and one scrolled past the top has to be dragged back.
extension XCTestCase {
    /// Scrolls down until [e] exists (a lazy list does not draw it before).
    func scrollToExist(_ app: XCUIApplication, _ e: XCUIElement) {
        for _ in 0..<20 where !e.exists {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)).press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)))
            sleep(1)
        }
    }

    /// Scrolls until [e] is inside the visible part (above the keyboard when it is up), in whichever direction it lies. A control below the fold or behind the
    /// keyboard is not hittable in any useful sense: a tap there would land on the keyboard.
    func reveal(_ app: XCUIApplication, _ e: XCUIElement) {
        for _ in 0..<25 {
            let keyboard = app.keyboards.firstMatch
            // the bottom bar (Home, Search, Reports, More) covers the lowest rows of a screen: a row behind it is not one a tap reaches
            let bar = app.descendants(matching: .any).matching(identifier: "nav_home").firstMatch
            let bottom = bar.exists && bar.isHittable ? bar.frame.minY : app.frame.maxY
            let limit = keyboard.exists ? keyboard.frame.minY : bottom
            if e.frame.minY >= 100 && e.frame.maxY <= limit - 4 && e.isHittable { return }
            // Drag inside the part above the keyboard: a swipe that starts on the keyboard would type. Above the top: drag down; below: drag up.
            let above = e.frame.minY < 100
            let mid = min(0.55, (limit - 20) / app.frame.height)
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: above ? 0.25 : mid))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: above ? mid : 0.2))
            from.press(forDuration: 0.05, thenDragTo: to)
            sleep(1) // let the fling settle: a tap on content that is still moving only stops it
        }
    }

    /// Waits until [e] has stopped moving: a tap on a list that is still flinging only stops the fling.
    func settle(_ e: XCUIElement) {
        var last = e.frame
        for _ in 0..<12 {
            usleep(400_000)
            let now = e.frame
            if now == last { return }
            last = now
        }
    }
}

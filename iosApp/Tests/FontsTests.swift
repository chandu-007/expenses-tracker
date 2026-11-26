import XCTest
import Shared

/// The design's fonts are really in the app bundle and really load (a missing file would silently fall back to the system font otherwise).
final class FontsTests: XCTestCase {
    func testTheSevenFontFilesAreInTheBundleAndLoad() {
        for name in ["Inter-400", "Inter-500", "Inter-600", "Inter-700", "SpaceGrotesk-500", "SpaceGrotesk-600", "SpaceGrotesk-700"] {
            XCTAssertNotNil(Bundle.main.path(forResource: name, ofType: "ttf"), "\(name).ttf is not in the app bundle")
        }
        XCTAssertNil(IosFonts.shared.check(), "the fonts did not load: \(IosFonts.shared.check() ?? "")")
    }
}

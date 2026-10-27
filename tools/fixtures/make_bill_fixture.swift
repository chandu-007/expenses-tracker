// Draws tools/fixtures/bill_fixture.png: an INVENTED shop receipt (the shop does not exist) with a header, three items, a subtotal, tax and a grand total.
// It is the picture the release smoke test (tools/release_smoke.py) gives to "Read items from a photo". The numbers are chosen so that the reader's answer is
// unambiguous: items 80.00 + 325.00 + 140.00 = 545.00, GST 5% = 27.25, TOTAL 572.25.
// Run: swift tools/fixtures/make_bill_fixture.swift tools/fixtures/bill_fixture.png   (macOS; CoreGraphics and CoreText only, no Python imaging library needed)
import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "bill_fixture.png"
let width = 900, height = 1050
let space = CGColorSpace(name: CGColorSpace.sRGB)!
guard let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0, space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { fatalError("no context") }

// white paper
ctx.setFillColor(CGColor(colorSpace: space, components: [1, 1, 1, 1])!)
ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))

let black = CGColor(colorSpace: space, components: [0, 0, 0, 1])!
let leftMargin: CGFloat = 60, rightMargin: CGFloat = CGFloat(width) - 60

/// Draws [text] with its baseline [y] points from the TOP of the picture; right-aligned when [right] is true (then [x] is the right edge).
func draw(_ text: String, y: CGFloat, size: CGFloat, bold: Bool = false, x: CGFloat = leftMargin, right: Bool = false, center: Bool = false) {
    let font = CTFontCreateWithName((bold ? "Helvetica-Bold" : "Helvetica") as CFString, size, nil)
    let attributes: [CFString: Any] = [kCTFontAttributeName: font, kCTForegroundColorAttributeName: black]
    let line = CTLineCreateWithAttributedString(CFAttributedStringCreate(nil, text as CFString, attributes as CFDictionary))
    let textWidth = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
    let startX = center ? (CGFloat(width) - textWidth) / 2 : (right ? x - textWidth : x)
    ctx.textPosition = CGPoint(x: startX, y: CGFloat(height) - y) // CoreGraphics counts from the bottom
    CTLineDraw(line, ctx)
}

func rule(y: CGFloat) {
    ctx.setStrokeColor(black); ctx.setLineWidth(3)
    ctx.move(to: CGPoint(x: leftMargin, y: CGFloat(height) - y)); ctx.addLine(to: CGPoint(x: rightMargin, y: CGFloat(height) - y)); ctx.strokePath()
}

func row(_ name: String, _ amount: String, y: CGFloat, bold: Bool = false) {
    draw(name, y: y, size: 46, bold: bold)
    draw(amount, y: y, size: 46, bold: bold, x: rightMargin, right: true)
}

draw("BLUE HERON PROVISIONS", y: 130, size: 54, bold: true, center: true)
draw("12 Lakeview Road", y: 190, size: 38, center: true)
draw("Cash Memo", y: 250, size: 38, center: true)
rule(y: 285)
row("Lemon Rice Pack", "80.00", y: 370)
row("Almond Butter 250g", "325.00", y: 450)
row("Green Tea Box", "140.00", y: 530)
rule(y: 575)
row("Sub Total", "545.00", y: 660)
row("GST 5%", "27.25", y: 740)
row("TOTAL", "572.25", y: 830, bold: true)
rule(y: 875)
draw("Thank you, visit again", y: 960, size: 38, center: true)

guard let image = ctx.makeImage(), let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: out) as CFURL, UTType.png.identifier as CFString, 1, nil) else { fatalError("cannot write \(out)") }
CGImageDestinationAddImage(dest, image, nil)
guard CGImageDestinationFinalize(dest) else { fatalError("cannot finish \(out)") }
print("wrote \(out) (\(width)x\(height))")

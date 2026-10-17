// Draws the 1024x1024 App Store icon from the same shapes as the Android adaptive icon (androidApp/src/main/res/drawable/ic_launcher_*.xml):
// the teal gradient, a translucent coin with a white ring, and the rupee mark. No alpha channel, as the App Store requires.
// Run: swift tools/make_ios_icon.swift iosApp/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let size = 1024
let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "icon-1024.png"
let space = CGColorSpace(name: CGColorSpace.sRGB)!
guard let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { fatalError("no context") }

// The Android drawing is 108 units, top-left origin; the mark is drawn a little larger here because iOS icons have no safe-zone crop.
let unit = CGFloat(size) / 108
ctx.translateBy(x: 0, y: CGFloat(size)); ctx.scaleBy(x: 1, y: -1)
ctx.scaleBy(x: unit, y: unit)

func hex(_ rgb: UInt32, _ a: CGFloat = 1) -> CGColor { CGColor(colorSpace: space, components: [CGFloat((rgb >> 16) & 255) / 255, CGFloat((rgb >> 8) & 255) / 255, CGFloat(rgb & 255) / 255, a])! }

let gradient = CGGradient(colorsSpace: space, colors: [hex(0x1B6B5F), hex(0x0B3B4A)] as CFArray, locations: [0, 1])!
ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 108, y: 108), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])

ctx.translateBy(x: 54, y: 54); ctx.scaleBy(x: 1.45, y: 1.45); ctx.translateBy(x: -54, y: -54)

let coin = CGRect(x: 54 - 27, y: 54 - 27, width: 54, height: 54)
ctx.setFillColor(hex(0xFFFFFF, 0.15)); ctx.fillEllipse(in: coin)
ctx.setStrokeColor(hex(0xFFFFFF)); ctx.setLineWidth(2.5); ctx.strokeEllipse(in: coin)

let mark = CGMutablePath()
mark.move(to: CGPoint(x: 42, y: 40)); mark.addLine(to: CGPoint(x: 66, y: 40))
mark.move(to: CGPoint(x: 42, y: 48)); mark.addLine(to: CGPoint(x: 66, y: 48))
mark.move(to: CGPoint(x: 42, y: 48)); mark.addLine(to: CGPoint(x: 49, y: 48))
mark.addCurve(to: CGPoint(x: 55, y: 58), control1: CGPoint(x: 57, y: 48), control2: CGPoint(x: 60, y: 54))
mark.addCurve(to: CGPoint(x: 45, y: 61), control1: CGPoint(x: 52, y: 60.5), control2: CGPoint(x: 48, y: 61))
mark.addLine(to: CGPoint(x: 58, y: 74))
ctx.setLineWidth(4.2); ctx.setLineCap(.round); ctx.setLineJoin(.round)
ctx.addPath(mark); ctx.strokePath()

let url = URL(fileURLWithPath: out)
try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
precondition(CGImageDestinationFinalize(dest))
print("wrote \(out)")

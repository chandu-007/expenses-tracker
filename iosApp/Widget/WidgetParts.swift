import SwiftUI
import UIKit
import WidgetKit

/// UI S8: what the iPhone's widgets share. The app (Kotlin, `IosWidgetData` with `WidgetFeeds.of`) writes every value into the App Group's preferences;
/// the widgets only read and draw. Deployment target iOS 17.0: everything here is iOS 16 or earlier unless marked.
let widgetGroup = "group.app.expenses.tracker.ios"
let lastAddedKey = "last_added"
/// How long a widget tap can be taken back (it is only queued until the app runs).
let undoWindow: TimeInterval = 120

/// The app's violet (#6650A3), the default accent: "+ Add" and the bars in full colour.
let appViolet = Color(red: 0.4, green: 0.31, blue: 0.64)

/// "Allow widgets" (the app's App lock screen). An app that has not run since the update has not written it yet: that counts as allowed, as in the app.
func widgetsAllowed(_ d: UserDefaults) -> Bool { (d.object(forKey: "widgets_allowed") as? Bool) ?? true }

struct QuickButton: Identifiable {
    let id: String
    let label: String
    let detail: String
}

/// The saved quick entries as the app wrote them ("id<TAB>name<TAB>amount", the amount already masked when anything hides the figures).
func quickButtons(_ d: UserDefaults) -> [QuickButton] {
    (d.stringArray(forKey: "quick_buttons") ?? []).compactMap { line -> QuickButton? in
        let p = line.components(separatedBy: "\t")
        return p.count >= 3 ? QuickButton(id: p[0], label: p[1], detail: p[2]) : nil
    }
}

/// "Added Chai" while the last widget tap can still be taken back, else nil.
func lastAdded(_ d: UserDefaults) -> String? {
    guard let l = d.string(forKey: lastAddedKey) else { return nil }
    let p = l.components(separatedBy: "\t")
    guard p.count >= 3, let at = TimeInterval(p[2]), Date().timeIntervalSince1970 - at / 1000 < undoWindow else { return nil }
    return "Added " + p[1]
}

/// The overall budget's pace as the app wrote it. Nil without a limit, or while Hide amounts or the App lock is on (the app then writes none).
struct PaceFeed {
    /// Share of the limit spent, 0 to 1000.
    let used: Int
    /// How far through the period today is, 0 to 1000: the marker.
    let today: Int
    /// "On pace", "A little ahead", ...
    let word: String
    /// ON_PACE, A_LITTLE_AHEAD, AHEAD, OVER or EARLY.
    let level: String
    /// "₹28.1K left of ₹60K".
    let line: String
    /// "₹28.1K" or "Over": the Lock Screen's round gauge.
    let leftShort: String
    /// The exact amounts, for VoiceOver.
    let spoken: String

    static func load(_ d: UserDefaults) -> PaceFeed? {
        guard d.object(forKey: "pace_used_permille") != nil, d.object(forKey: "pace_today_permille") != nil else { return nil }
        let used = d.integer(forKey: "pace_used_permille")
        let today = d.integer(forKey: "pace_today_permille")
        guard used >= 0, today >= 0 else { return nil }
        return PaceFeed(
            used: min(used, 1000), today: min(today, 1000), word: d.string(forKey: "pace_word") ?? "", level: d.string(forKey: "pace_level") ?? "",
            line: d.string(forKey: "budget_line") ?? "", leftShort: d.string(forKey: "budget_left_short") ?? "", spoken: d.string(forKey: "pace_spoken") ?? "")
    }
}

/// A colour for light and dark (UIColor's dynamic provider, iOS 13).
private func adaptive(light: UIColor, dark: UIColor) -> Color {
    Color(UIColor { $0.userInterfaceStyle == .dark ? dark : light })
}

private func rgb(_ hex: UInt32) -> UIColor {
    UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

/// The pace word's colour in full colour, the same pairs as Android's widgets (4.5:1 or better on the widget's background): green on pace, amber ahead, red over.
/// In the tinted and clear looks (and on the Lock Screen) the system draws in one tint, so the word is plain there.
func paceColor(_ level: String, _ mode: WidgetRenderingMode) -> Color {
    guard mode == .fullColor else { return .primary }
    switch level {
    case "ON_PACE": return adaptive(light: rgb(0x0F6B41), dark: rgb(0x6FD3A0))
    case "A_LITTLE_AHEAD", "AHEAD": return adaptive(light: rgb(0x8A5300), dark: rgb(0xFFB95C))
    case "OVER": return adaptive(light: rgb(0xA21F19), dark: rgb(0xFF8A80))
    default: return .secondary
    }
}

/// The budget bar: the share of the limit spent, with a 2 pt marker where today is in the period. VoiceOver reads the exact amounts ([PaceFeed.spoken]) once.
struct PaceBar: View {
    let pace: PaceFeed
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        GeometryReader { g in
            let w = g.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(.quaternary).frame(height: 6)
                Capsule().fill(barColor).frame(width: max(6, w * CGFloat(pace.used) / 1000), height: 6).widgetAccentable()
                Rectangle().fill(.primary).frame(width: 2, height: 10).offset(x: min(max(0, w * CGFloat(pace.today) / 1000 - 1), max(0, w - 2)))
            }
            .frame(width: w, height: 10, alignment: .leading)
        }
        .frame(height: 10)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(pace.spoken))
    }

    private var barColor: Color {
        guard mode == .fullColor else { return .primary }
        return pace.level == "OVER" ? adaptive(light: rgb(0xA21F19), dark: rgb(0xFF8A80)) : appViolet
    }
}

/// "+ Add": the app's violet with white text in full colour; in the tinted and clear looks a faint capsule in the system's tint (no hard-coded colour that would
/// turn into a flat block there).
struct AddCapsule: View {
    var label: String = "+ Add"
    @Environment(\.widgetRenderingMode) private var mode

    var body: some View {
        Text(label).font(.subheadline.bold())
            .foregroundStyle(mode == .fullColor ? Color.white : Color.primary)
            .padding(.horizontal, label.count > 1 ? 14 : 12).padding(.vertical, 6)
            .background(Capsule().fill(mode == .fullColor ? appViolet : Color.primary.opacity(0.18)))
            .widgetAccentable()
            .accessibilityLabel("Add an expense")
    }
}

/// A figure: the amount (accented in the tinted look), or, when it is masked, what VoiceOver should say instead of "rupee bullet bullet".
struct Figure: View {
    let text: String
    let masked: Bool
    var font: Font = .title2.bold()

    var body: some View {
        Text(text).font(font).lineLimit(1).minimumScaleFactor(0.6).widgetAccentable()
            .accessibilityLabel(masked ? Text("Amount hidden") : Text(text))
    }
}

/// What every widget shows while "Allow widgets" is off in the app: nothing from the ledger, only where to turn it back on.
struct WidgetsOffNote: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Widgets are off").font(.headline)
            Text("Turn them on in Expenses: More, App lock.").font(.caption2).foregroundStyle(.secondary).lineLimit(3)
        }
    }
}

/// One quick entry as a button: a tap queues it (see `QuickAddIntent`).
struct QuickChip: View {
    let button: QuickButton
    var showDetail = true

    var body: some View {
        Button(intent: QuickAddIntent(templateId: button.id, label: button.label)) {
            HStack {
                Text(button.label).font(.footnote.weight(.medium)).lineLimit(1)
                if showDetail {
                    Spacer(minLength: 2)
                    Text(button.detail).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 10).fill(.fill.quaternary))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(showDetail && !button.detail.contains("•") ? "Add \(button.label), \(button.detail)" : "Add \(button.label)")
    }
}

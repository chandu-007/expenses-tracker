import SwiftUI
import WidgetKit

/// UI S8 (backlog N-10): the Lock Screen widgets (also on the Always-On display and in StandBy where iOS puts them): round, rectangular and the one line above the clock.
///
/// Privacy, in three layers:
/// 1. What the app writes (`WidgetFeeds.of`): nothing at all while "Allow widgets" is off; only masked text and no pace while Hide amounts or the App lock is on.
/// 2. The same eye as the Home Screen widgets (`widget_revealed`, off until the person taps it): masked, these widgets show no figure, no bar and no quick entry, only
///    "Expenses" and Add, so nothing personal is on the Lock Screen by default.
/// 3. Every figure, bar and per-day line is `.privacySensitive()`: iOS redacts it while the iPhone is locked, so even a shown figure appears only after Face ID or the passcode.
///    (Whether a placed widget is redacted depends on the person's settings; it must be checked on a device: the simulator does not show the locked state reliably.)
/// The Lock Screen draws in one tint (`vibrant`): no colour is used here.
struct LockEntry: TimelineEntry {
    let date: Date
    let allowed: Bool
    let shown: Bool
    let canToggle: Bool
    let today: String?
    let perDay: String?
    let pace: PaceFeed?
}

private func loadLock() -> LockEntry {
    guard let d = UserDefaults(suiteName: widgetGroup), d.string(forKey: "published_at") != nil else {
        return LockEntry(date: Date(), allowed: true, shown: false, canToggle: false, today: nil, perDay: nil, pace: nil)
    }
    let allowed = widgetsAllowed(d)
    let can = allowed && d.bool(forKey: "can_toggle")
    let shown = can && d.bool(forKey: "widget_revealed")
    let perDay = shown ? d.string(forKey: "today_per_day") : nil
    return LockEntry(
        date: Date(), allowed: allowed, shown: shown, canToggle: can, today: shown ? d.string(forKey: "today_spent") : nil,
        perDay: (perDay?.isEmpty ?? true) ? nil : perDay, pace: shown ? PaceFeed.load(d) : nil)
}

struct LockProvider: TimelineProvider {
    func placeholder(in context: Context) -> LockEntry { LockEntry(date: Date(), allowed: true, shown: false, canToggle: false, today: nil, perDay: nil, pace: nil) }
    func getSnapshot(in context: Context, completion: @escaping (LockEntry) -> Void) { completion(loadLock()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<LockEntry>) -> Void) {
        // the app asks for a redraw whenever its data changes; a new day is covered by asking again later
        completion(Timeline(entries: [loadLock()], policy: .after(Date().addingTimeInterval(30 * 60))))
    }
}

struct LockView: View {
    let entry: LockEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        content.widgetURL(URL(string: "expenses://add"))
    }

    @ViewBuilder
    private var content: some View {
        switch family {
        case .accessoryCircular: circular
        case .accessoryInline: inline
        default: rectangular
        }
    }

    /// Masked or not allowed: a plus that opens Add. Shown with a limit: what is left of it as a gauge ("₹28.1K", or "Over").
    @ViewBuilder
    private var circular: some View {
        if let pace = entry.pace, !pace.leftShort.isEmpty {
            Gauge(value: Double(1000 - pace.used), in: 0...1000) {
                Text("left")
            } currentValueLabel: {
                Text(pace.leftShort).minimumScaleFactor(0.5)
            }
            .gaugeStyle(.accessoryCircularCapacity)
            .privacySensitive()
            .accessibilityLabel(Text(pace.spoken))
            .containerBackground(for: .widget) { AccessoryWidgetBackground() }
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "plus").font(.title2.weight(.semibold))
            }
            .accessibilityLabel("Add an expense")
            .containerBackground(for: .widget) { Color.clear }
        }
    }

    /// One line above the clock: "₹1,240.00 today", or "Add expense".
    @ViewBuilder
    private var inline: some View {
        if entry.shown, let today = entry.today {
            Text("\(today) today").privacySensitive()
                .containerBackground(for: .widget) { Color.clear }
        } else {
            Label("Add expense", systemImage: "plus")
                .containerBackground(for: .widget) { Color.clear }
        }
    }

    /// "Expenses · today", the figure, the pace bar and the per-day line; masked: the app's name, Add, and the eye when the figures may be shown.
    @ViewBuilder
    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 2) {
            if entry.shown, let today = entry.today {
                Text("Expenses · today").font(.caption2).foregroundStyle(.secondary)
                Text(today).font(.headline).lineLimit(1).minimumScaleFactor(0.6).widgetAccentable().privacySensitive()
                if let pace = entry.pace { PaceBar(pace: pace).privacySensitive() }
                if let perDay = entry.perDay { Text(perDay).font(.caption2).lineLimit(1).minimumScaleFactor(0.7).privacySensitive() }
            } else {
                HStack {
                    Label("Expenses", systemImage: "plus.circle").font(.headline)
                    Spacer(minLength: 0)
                    if entry.canToggle {
                        Button(intent: ToggleAmountsIntent()) { Image(systemName: "eye") }.buttonStyle(.plain).accessibilityLabel("Show the amounts")
                    }
                }
                Text(entry.allowed ? "Tap to add an expense" : "Widgets are off in Expenses").font(.caption2).foregroundStyle(.secondary).lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { Color.clear }
    }
}

struct LockScreenWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ExpensesLockScreen", provider: LockProvider()) { LockView(entry: $0) }
            .configurationDisplayName("Expenses")
            .description("Add an expense from the Lock Screen. Today's spending and the budget's pace appear only after you tap the eye on an Expenses widget, never while Hide amounts or the app lock is on, and are hidden while the iPhone is locked.")
            .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

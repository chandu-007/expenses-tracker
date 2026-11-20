import AppIntents
import SwiftUI
import WidgetKit

/// The Today widget: what was spent today and what the budget leaves for each remaining day, as on Android. The app writes the strings (Kotlin,
/// IosWidgetData) into the App Group's preferences whenever its data changes; a widget cannot open the app's database.
/// UI S8 (U-23): shown, it is one number, the pace line ("₹1,565.00 a day keeps you on pace") and the budget's pace bar; masked, it says so and keeps the
/// first quick entry as a button, so it is never an empty card. "Allow widgets" off: it shows nothing from the ledger.
struct TodayEntry: TimelineEntry {
    let date: Date
    let allowed: Bool
    let spent: String?
    let perDay: String?
    let canToggle: Bool
    let revealed: Bool
    let pace: PaceFeed?
    let chip: QuickButton?
    let lastText: String?

    static func empty(spent: String? = nil) -> TodayEntry {
        TodayEntry(date: Date(), allowed: true, spent: spent, perDay: nil, canToggle: false, revealed: false, pace: nil, chip: nil, lastText: nil)
    }
}

private func load() -> TodayEntry {
    guard let d = UserDefaults(suiteName: widgetGroup), d.string(forKey: "published_at") != nil else { return .empty() }
    let allowed = widgetsAllowed(d)
    let can = allowed && d.bool(forKey: "can_toggle")
    let shown = can && d.bool(forKey: "widget_revealed")
    // Hide amounts or the app lock on: the app wrote only masked text, and both keys hold it.
    let spent = shown ? d.string(forKey: "today_spent") : d.string(forKey: "today_spent_masked")
    let perDay = shown ? d.string(forKey: "today_per_day") : d.string(forKey: "today_per_day_masked")
    return TodayEntry(
        date: Date(), allowed: allowed, spent: allowed ? spent : nil, perDay: (perDay?.isEmpty ?? true) ? nil : perDay, canToggle: can, revealed: shown,
        pace: shown ? PaceFeed.load(d) : nil, chip: allowed && !shown ? quickButtons(d).first : nil, lastText: allowed ? lastAdded(d) : nil)
}

/// The eye: flips whether the figures are shown, for every copy of every widget (Today, Quick add and the Lock Screen ones). Nothing while widgets are not allowed.
struct ToggleAmountsIntent: AppIntent {
    static var title: LocalizedStringResource = "Show or hide the amounts"
    func perform() async throws -> some IntentResult {
        guard let d = UserDefaults(suiteName: widgetGroup), widgetsAllowed(d) else { return .result() }
        d.set(!d.bool(forKey: "widget_revealed"), forKey: "widget_revealed")
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry { .empty(spent: "₹••••") }
    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) { completion(load()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        // the app asks for a redraw whenever its data changes; the clock moving on (a new day) is covered by asking again later, and the "Added" line goes by itself
        let entry = load()
        let next = entry.lastText == nil ? Date().addingTimeInterval(30 * 60) : Date().addingTimeInterval(undoWindow + 5)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

struct TodayView: View {
    let entry: TodayEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("Spent today").font(.caption2).foregroundStyle(.secondary)
                Spacer()
                if entry.canToggle {
                    Button(intent: ToggleAmountsIntent()) { Image(systemName: entry.revealed ? "eye.slash" : "eye").font(.caption) }
                        .buttonStyle(.plain).foregroundStyle(.secondary)
                        .accessibilityLabel(entry.revealed ? "Hide the amounts" : "Show the amounts")
                }
            }
            if !entry.allowed {
                WidgetsOffNote()
            } else {
                Figure(text: entry.spent ?? "Open the app", masked: entry.spent != nil && !entry.revealed)
                if entry.revealed {
                    if let perDay = entry.perDay { Text(perDay).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                    if let pace = entry.pace { PaceBar(pace: pace).padding(.top, 4) }
                } else if entry.spent != nil {
                    Text("Amounts hidden").font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if let last = entry.lastText {
                HStack(spacing: 4) {
                    Text(last).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    Spacer(minLength: 0)
                    Button(intent: UndoQuickAddIntent()) { Text("Undo").font(.caption2.bold()) }.buttonStyle(.plain)
                }
            } else {
                HStack(spacing: 6) {
                    if let chip = entry.chip { QuickChip(button: chip, showDetail: false) }
                    Spacer(minLength: 0)
                    Link(destination: URL(string: "expenses://add")!) { AddCapsule(label: entry.chip == nil ? "+ Add" : "+") }
                }
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ExpensesToday", provider: TodayProvider()) { TodayView(entry: $0) }
            .configurationDisplayName("Spent today")
            .description("What you spent today, what is left to spend each day and the budget's pace. Amounts stay hidden until you tap the eye, and always while Hide amounts or the app lock is on.")
            .supportedFamilies([.systemSmall])
    }
}

/// The widgets of the extension. The Control Center control needs iOS 18: on iOS 17 the bundle without it is offered (SE-0360: the two branches may differ).
@main
struct ExpensesWidgets: WidgetBundle {
    var body: some Widget {
        if #available(iOSApplicationExtension 18.0, *) {
            return withControl
        } else {
            return withoutControl
        }
    }

    @WidgetBundleBuilder
    private var withoutControl: some Widget {
        TodayWidget()
        QuickAddWidget()
        LockScreenWidget()
    }

    @available(iOSApplicationExtension 18.0, *)
    @WidgetBundleBuilder
    private var withControl: some Widget {
        TodayWidget()
        QuickAddWidget()
        LockScreenWidget()
        AddExpenseControl()
    }
}

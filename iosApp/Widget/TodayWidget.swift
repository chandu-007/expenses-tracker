import AppIntents
import SwiftUI
import WidgetKit

/// The Today widget: what was spent today and what the budget leaves for each remaining day, as on Android. The app writes the strings (Kotlin,
/// IosWidgetData) into the App Group's preferences whenever its data changes; a widget cannot open the app's database.
private let group = "group.app.expenses.tracker.ios"

struct TodayEntry: TimelineEntry {
    let date: Date
    let spent: String?
    let perDay: String?
    let canToggle: Bool
    let revealed: Bool
}

private func load() -> TodayEntry {
    let d = UserDefaults(suiteName: group)
    guard let d, d.string(forKey: "published_at") != nil else { return TodayEntry(date: Date(), spent: nil, perDay: nil, canToggle: false, revealed: false) }
    let can = d.bool(forKey: "can_toggle")
    let shown = can && d.bool(forKey: "widget_revealed")
    // Hide amounts or the app lock on: the app wrote only masked text, and both keys hold it.
    let spent = shown ? d.string(forKey: "today_spent") : d.string(forKey: "today_spent_masked")
    let perDay = shown ? d.string(forKey: "today_per_day") : d.string(forKey: "today_per_day_masked")
    return TodayEntry(date: Date(), spent: spent, perDay: (perDay?.isEmpty ?? true) ? nil : perDay, canToggle: can, revealed: shown)
}

/// The eye: flips whether the figures are shown, for every copy of the widget.
struct ToggleAmountsIntent: AppIntent {
    static var title: LocalizedStringResource = "Show or hide the amounts"
    func perform() async throws -> some IntentResult {
        let d = UserDefaults(suiteName: group)
        d?.set(!(d?.bool(forKey: "widget_revealed") ?? false), forKey: "widget_revealed")
        return .result()
    }
}

struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry { TodayEntry(date: Date(), spent: "₹••••", perDay: nil, canToggle: false, revealed: false) }
    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) { completion(load()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        // the app asks for a redraw whenever its data changes; the clock moving on (a new day) is covered by asking again later
        completion(Timeline(entries: [load()], policy: .after(Date().addingTimeInterval(30 * 60))))
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
                }
            }
            Text(entry.spent ?? "Open the app").font(.title2.bold()).lineLimit(1).minimumScaleFactor(0.6)
            if let perDay = entry.perDay { Text(perDay).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
            Spacer(minLength: 0)
            Link(destination: URL(string: "expenses://add")!) {
                Text("+ Add").font(.subheadline.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 6).background(Capsule().fill(Color(red: 0.4, green: 0.31, blue: 0.64)))
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ExpensesToday", provider: TodayProvider()) { TodayView(entry: $0) }
            .configurationDisplayName("Spent today")
            .description("What you spent today and what is left to spend each day. Amounts stay hidden until you tap the eye, and always while Hide amounts or the app lock is on.")
            .supportedFamilies([.systemSmall])
    }
}

@main
struct ExpensesWidgets: WidgetBundle {
    var body: some Widget { TodayWidget() }
}

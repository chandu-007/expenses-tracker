import AppIntents
import SwiftUI
import WidgetKit

/// The Quick add widget: this month's spending, "+ Add", and the saved quick entries as one-tap buttons, like Android's widget. A widget cannot reach the app's
/// database, so a tap only queues the entry in the App Group's preferences; the app records it (stamped with the time of the tap) the next time it runs, which
/// the widget says. Undo takes the tap back while it is still queued.
private let group = "group.app.expenses.tracker.ios"
private let queueKey = "quick_adds"
private let lastKey = "last_added"
private let undoWindow: TimeInterval = 120

struct QuickButton: Identifiable {
    let id: String
    let label: String
    let detail: String
}

struct QuickEntryState: TimelineEntry {
    let date: Date
    let month: String?
    let canToggle: Bool
    let revealed: Bool
    let buttons: [QuickButton]
    let lastText: String?
}

private func loadQuick() -> QuickEntryState {
    let d = UserDefaults(suiteName: group)
    guard let d, d.string(forKey: "published_at") != nil else { return QuickEntryState(date: Date(), month: nil, canToggle: false, revealed: false, buttons: [], lastText: nil) }
    let can = d.bool(forKey: "can_toggle")
    let shown = can && d.bool(forKey: "widget_revealed")
    let buttons = (d.stringArray(forKey: "quick_buttons") ?? []).compactMap { line -> QuickButton? in
        let p = line.components(separatedBy: "\t")
        return p.count >= 3 ? QuickButton(id: p[0], label: p[1], detail: p[2]) : nil
    }
    var last: String? = nil
    if let l = d.string(forKey: lastKey) {
        let p = l.components(separatedBy: "\t")
        if p.count >= 3, let at = TimeInterval(p[2]), Date().timeIntervalSince1970 - at / 1000 < undoWindow { last = "Added " + p[1] }
    }
    return QuickEntryState(date: Date(), month: shown ? d.string(forKey: "month_spent") : d.string(forKey: "month_spent_masked"), canToggle: can, revealed: shown, buttons: buttons, lastText: last)
}

/// A tap on a quick entry: queued with the time of the tap, and remembered for Undo.
struct QuickAddIntent: AppIntent {
    static var title: LocalizedStringResource = "Add a quick entry"
    @Parameter(title: "Entry") var templateId: String
    @Parameter(title: "Name") var label: String

    init() {}
    init(templateId: String, label: String) { self.templateId = templateId; self.label = label }

    func perform() async throws -> some IntentResult {
        guard let d = UserDefaults(suiteName: group) else { return .result() }
        let at = Int64(Date().timeIntervalSince1970 * 1000)
        var queue = d.stringArray(forKey: queueKey) ?? []
        queue.append("\(templateId)\t\(at)")
        d.set(Array(queue.suffix(50)), forKey: queueKey)
        d.set("\(templateId)\t\(label)\t\(at)", forKey: lastKey)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// Takes the last tap back, but only while the app has not yet recorded it.
struct UndoQuickAddIntent: AppIntent {
    static var title: LocalizedStringResource = "Undo the last quick entry"
    func perform() async throws -> some IntentResult {
        guard let d = UserDefaults(suiteName: group), let l = d.string(forKey: lastKey) else { return .result() }
        let p = l.components(separatedBy: "\t")
        if p.count >= 3 {
            var queue = d.stringArray(forKey: queueKey) ?? []
            if let i = queue.lastIndex(of: "\(p[0])\t\(p[2])") { queue.remove(at: i) }
            if queue.isEmpty { d.removeObject(forKey: queueKey) } else { d.set(queue, forKey: queueKey) }
        }
        d.removeObject(forKey: lastKey)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

struct QuickProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickEntryState { QuickEntryState(date: Date(), month: "₹••••", canToggle: false, revealed: false, buttons: [], lastText: nil) }
    func getSnapshot(in context: Context, completion: @escaping (QuickEntryState) -> Void) { completion(loadQuick()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickEntryState>) -> Void) {
        let entry = loadQuick()
        // the "Added · Undo" line goes away by itself once its two minutes are over
        let next = entry.lastText == nil ? Date().addingTimeInterval(30 * 60) : Date().addingTimeInterval(125)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

struct QuickAddView: View {
    let entry: QuickEntryState
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Spent this month").font(.caption2).foregroundStyle(.secondary)
                    Text(entry.month ?? "Open the app").font(.title3.bold()).lineLimit(1).minimumScaleFactor(0.6)
                }
                Spacer()
                if entry.canToggle {
                    Button(intent: ToggleAmountsIntent()) { Image(systemName: entry.revealed ? "eye.slash" : "eye").font(.caption) }
                        .buttonStyle(.plain).foregroundStyle(.secondary)
                }
                Link(destination: URL(string: "expenses://add")!) {
                    Text("+ Add").font(.subheadline.bold()).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 6).background(Capsule().fill(Color(red: 0.4, green: 0.31, blue: 0.64)))
                }
            }
            if let last = entry.lastText {
                HStack {
                    Text(last + ". Counts when you open Expenses.").font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    Spacer()
                    Button(intent: UndoQuickAddIntent()) { Text("Undo").font(.caption2.bold()) }.buttonStyle(.plain)
                }
            }
            if entry.buttons.isEmpty {
                Text("Save a quick entry in Expenses (add an expense and tick Save as a quick entry) and it shows here.").font(.caption2).foregroundStyle(.secondary)
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 6), GridItem(.flexible(), spacing: 6)], spacing: 6) {
                    ForEach(entry.buttons) { b in
                        Button(intent: QuickAddIntent(templateId: b.id, label: b.label)) {
                            HStack {
                                Text(b.label).font(.footnote.weight(.medium)).lineLimit(1)
                                Spacer(minLength: 2)
                                Text(b.detail).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                            }
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 10).fill(.fill.quaternary))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

struct QuickAddWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ExpensesQuickAdd", provider: QuickProvider()) { QuickAddView(entry: $0) }
            .configurationDisplayName("Quick add")
            .description("One-tap buttons for your saved quick entries and this month's spending. A tapped entry is recorded the next time Expenses runs. Amounts stay hidden until you tap the eye, and always while Hide amounts or the app lock is on.")
            .supportedFamilies([.systemMedium])
    }
}

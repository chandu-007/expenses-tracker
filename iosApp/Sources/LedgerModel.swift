import Foundation
import ExpensesCore

/// The screens' one source of truth: reads everything from the shared Kotlin core after each change.
@MainActor
final class LedgerModel: ObservableObject {
    let core: AppCore
    @Published var rows: [EntryRow] = []
    @Published var monthSpend: String = ""
    @Published var accounts: [AccountInfo] = []
    @Published var offer: SimilarOffer?
    @Published var message: String?
    private var offerTask: Task<Void, Never>?
    private var quiet = Set<String>()

    init() {
        // UI tests start from an empty ledger
        if ProcessInfo.processInfo.arguments.contains("-reset-data"),
           let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            try? FileManager.default.removeItem(at: support.appendingPathComponent("databases"))
        }
        core = IosKt.openAppCore()
        reload()
    }

    func reload() {
        rows = core.recent(limit: 200)
        monthSpend = core.monthSpendText()
        accounts = core.accounts()
    }

    func categories(income: Bool) -> [CategoryInfo] { core.categories(income: income) }

    /// Returns an error message, or nil when saved. The similar-entries offer follows a moment later, off the save path.
    func add(income: Bool, amount: String, category: String, account: String, note: String) -> String? {
        do {
            let id = try core.add(income: income, amountText: amount, categoryId: category, accountId: account, note: note)
            reload()
            offerLater(entryId: id)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    func delete(_ id: String) {
        try? core.delete(entryId: id)
        reload()
    }

    private func offerLater(entryId: String) {
        offerTask?.cancel()
        offerTask = Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            if Task.isCancelled { return }
            if let o = core.similarTo(entryId: entryId), !quiet.contains(o.label.lowercased() + "|" + o.categoryId) { offer = o }
        }
    }

    func dismissOffer() {
        if let o = offer { quiet.insert(o.label.lowercased() + "|" + o.categoryId) }
        offer = nil
    }

    func apply(_ ids: [String]) {
        guard let o = offer else { return }
        quiet.insert(o.label.lowercased() + "|" + o.categoryId)
        offer = nil
        if !ids.isEmpty {
            if let n = try? core.fileUnder(entryIds: ids, categoryId: o.categoryId) { message = "Moved \(n) to \(o.categoryName)" }
            reload()
        }
    }
}

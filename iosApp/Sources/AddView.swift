import SwiftUI
import ExpensesCore

struct AddView: View {
    @EnvironmentObject var model: LedgerModel
    @Environment(\.dismiss) private var dismiss
    @State private var income = false
    @State private var amount = ""
    @State private var note = ""
    @State private var category = "Food"
    @State private var account = "cash"
    @State private var problem: String?
    @FocusState private var focus: Bool

    var body: some View {
        NavigationStack {
            Form {
                Picker("Kind", selection: $income) { Text("Expense").tag(false); Text("Income").tag(true) }
                    .pickerStyle(.segmented).accessibilityIdentifier("kind")
                    .onChange(of: income) { _, now in category = model.categories(income: now).first?.id ?? "" }
                Section("Amount (₹)") {
                    TextField("0.00", text: $amount).keyboardType(.decimalPad).font(.title2).focused($focus).accessibilityIdentifier("amount")
                }
                Section("Note or merchant") { TextField("e.g. iCloud", text: $note).accessibilityIdentifier("note") }
                Section("Category") {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 8)], alignment: .leading, spacing: 8) {
                        ForEach(model.categories(income: income), id: \.id) { c in
                            Button { category = c.id } label: {
                                Text(c.name).font(.subheadline).lineLimit(1).padding(.horizontal, 10).padding(.vertical, 7).frame(maxWidth: .infinity)
                                    .background(category == c.id ? Theme.accent.opacity(0.2) : Color.secondary.opacity(0.12), in: Capsule())
                                    .overlay(Capsule().stroke(category == c.id ? Theme.accent : .clear, lineWidth: 1.5))
                            }.buttonStyle(.plain).accessibilityIdentifier("category_" + c.id)
                        }
                    }
                }
                Section("Account") {
                    Picker("Account", selection: $account) { ForEach(model.accounts, id: \.id) { Text($0.name).tag($0.id) } }.pickerStyle(.segmented)
                }
                if let p = problem { Text(p).foregroundStyle(.red).accessibilityIdentifier("problem") }
            }
            .navigationTitle(income ? "Add income" : "Add expense").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() }.accessibilityIdentifier("save") }
            }
            .onAppear { focus = true }
        }
    }

    private func save() {
        problem = model.add(income: income, amount: amount, category: category, account: account, note: note)
        if problem == nil { dismiss() }
    }
}

import SwiftUI
import ExpensesCore

struct CategoriesView: View {
    @EnvironmentObject var model: LedgerModel
    @State private var income = false

    var body: some View {
        NavigationStack {
            List {
                Picker("Kind", selection: $income) { Text("Expenses").tag(false); Text("Income").tag(true) }.pickerStyle(.segmented).accessibilityIdentifier("cat_tabs")
                ForEach(model.categories(income: income), id: \.id) { c in
                    Text(c.name).padding(.leading, c.parentId == nil ? 0 : 20)
                }
            }
            .navigationTitle("Categories")
        }
    }
}

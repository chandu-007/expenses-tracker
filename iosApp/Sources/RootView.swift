import SwiftUI
import ExpensesCore

struct RootView: View {
    @EnvironmentObject var model: LedgerModel
    @State private var adding = false

    var body: some View {
        TabView {
            HomeView(adding: $adding)
                .tabItem { Label("Home", systemImage: "house") }
            CategoriesView()
                .tabItem { Label("Categories", systemImage: "tag") }
        }
        .sheet(isPresented: $adding) { AddView().environmentObject(model) }
        .sheet(item: Binding(get: { model.offer.map { OfferBox(offer: $0) } }, set: { if $0 == nil { model.dismissOffer() } })) { box in
            SimilarView(offer: box.offer).environmentObject(model).presentationDetents([.medium, .large])
        }
        .overlay(alignment: .top) {
            if let m = model.message {
                Text(m).padding(12).background(.thinMaterial, in: Capsule()).padding(.top, 8)
                    .task { try? await Task.sleep(nanoseconds: 2_500_000_000); model.message = nil }
            }
        }
    }
}

struct OfferBox: Identifiable { let offer: SimilarOffer; var id: String { offer.label + offer.categoryId } }

struct HomeView: View {
    @EnvironmentObject var model: LedgerModel
    @Binding var adding: Bool

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Spent this month").font(.subheadline).foregroundStyle(.white.opacity(0.85))
                        Text(model.monthSpend).font(.system(size: 38, weight: .bold)).foregroundStyle(.white).accessibilityIdentifier("month_spend")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(20)
                    .background(LinearGradient(colors: [Theme.heroTop, Theme.heroBottom], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 26))
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                }
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(model.accounts, id: \.id) { a in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(a.name).font(.subheadline.weight(.semibold))
                                    Text(a.balanceText).font(.headline)
                                }
                                .foregroundStyle(.white).padding(14).frame(width: 170, alignment: .leading)
                                .background(a.id == "bank" ? Color(red: 0.12, green: 0.40, blue: 0.75) : Color(red: 0.18, green: 0.49, blue: 0.20), in: RoundedRectangle(cornerRadius: 18))
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
                }
                if model.rows.isEmpty {
                    Text("No expenses yet. Tap + to add one.").foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 30)
                } else {
                    ForEach(days(), id: \.0) { day, items in
                        Section(day) {
                            ForEach(items, id: \.id) { r in EntryLine(row: r) }
                                .onDelete { idx in idx.map { items[$0].id }.forEach { model.delete($0) } }
                        }
                    }
                }
            }
            .navigationTitle("Expenses")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { adding = true } label: { Image(systemName: "plus.circle.fill").font(.title2) }.accessibilityIdentifier("add") } }
        }
    }

    private func days() -> [(String, [EntryRow])] {
        var order: [String] = []; var by: [String: [EntryRow]] = [:]
        for r in model.rows { if by[r.dayText] == nil { order.append(r.dayText); by[r.dayText] = [] }; by[r.dayText]!.append(r) }
        return order.map { ($0, by[$0]!) }
    }
}

struct EntryLine: View {
    let row: EntryRow
    var body: some View {
        HStack(spacing: 12) {
            let label = row.categoryName.isEmpty ? row.title : row.categoryName
            let tint = Theme.tiles[abs(label.lowercased().hashValue) % Theme.tiles.count]
            Text(String(label.prefix(1)).uppercased()).font(.headline).foregroundStyle(.black.opacity(0.85))
                .frame(width: 40, height: 40).background(tint, in: RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 2) {
                Text(row.title).font(.body).lineLimit(1)
                Text(row.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Text((row.income ? "+" : "-") + row.amountText).font(.body.weight(.semibold)).foregroundStyle(row.income ? Theme.into : Theme.out)
        }
        .accessibilityIdentifier("row_" + row.title)
    }
}

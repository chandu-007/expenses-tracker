import SwiftUI
import ExpensesCore

/// "These earlier entries look like the one you just filed": move all, go through them one by one, or leave them. Nothing changes until the person agrees.
struct SimilarView: View {
    @EnvironmentObject var model: LedgerModel
    let offer: SimilarOffer
    @State private var reviewing = false
    @State private var index = 0
    @State private var chosen: [String] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if !reviewing { summary } else { review }
        }
        .padding(22).frame(maxHeight: .infinity, alignment: .top)
    }

    private var n: Int { offer.rows.count }

    private var summary: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Same as \"\(offer.label)\"?").font(.title3.bold())
            Text("\(n) earlier \(n == 1 ? "entry looks" : "entries look") like this one and \(n == 1 ? "is" : "are") filed elsewhere. File \(n == 1 ? "it" : "them") under \(offer.categoryName) too?")
                .accessibilityIdentifier("similar_text")
            Button { model.apply(offer.rows.map { $0.id }) } label: { Text(n == 1 ? "Move it" : "Move all \(n)").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).accessibilityIdentifier("similar_all")
            if n > 1 { Button { reviewing = true } label: { Text("Review one by one").frame(maxWidth: .infinity) }.buttonStyle(.bordered).accessibilityIdentifier("similar_review") }
            Button("Not now") { model.dismissOffer() }.accessibilityIdentifier("similar_not_now")
        }
    }

    private var review: some View {
        let row = offer.rows[index]
        return VStack(alignment: .leading, spacing: 12) {
            Text("\(index + 1) of \(n)").font(.headline).accessibilityIdentifier("similar_progress")
            Text(row.title).font(.title3.bold())
            Text("\(row.dayText) · \(row.amountText)").foregroundStyle(.secondary)
            Text("Now in \(row.categoryName). Move to \(offer.categoryName)?")
            HStack {
                Button("Skip") { next(move: false) }.buttonStyle(.bordered).accessibilityIdentifier("similar_skip")
                Button("Move") { next(move: true) }.buttonStyle(.borderedProminent).accessibilityIdentifier("similar_yes")
            }
            HStack {
                Button("Move all the rest") { model.apply(chosen + offer.rows[index...].map { $0.id }) }
                Spacer()
                Button("Done (\(chosen.count))") { model.apply(chosen) }
            }.font(.subheadline)
        }
    }

    private func next(move: Bool) {
        if move { chosen.append(offer.rows[index].id) }
        if index + 1 >= n { model.apply(chosen) } else { index += 1 }
    }
}

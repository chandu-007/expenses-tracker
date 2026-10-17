import SwiftUI
import ExpensesCore

@main
struct ExpensesApp: App {
    @StateObject private var model = LedgerModel()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(model).tint(Theme.accent)
        }
    }
}

enum Theme {
    /// deep purple on light screens, a lighter one on dark so buttons stay readable
    static let accent = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 0.67, green: 0.58, blue: 0.95, alpha: 1) : UIColor(red: 0.31, green: 0.22, blue: 0.55, alpha: 1) })
    static let heroTop = Color(red: 0.36, green: 0.27, blue: 0.62)
    static let heroBottom = Color(red: 0.24, green: 0.16, blue: 0.45)
    static let out = Color(red: 0.70, green: 0.15, blue: 0.12)
    static let into = Color(red: 0.09, green: 0.45, blue: 0.29)
    static let tiles: [Color] = [Color(red: 0.75, green: 0.89, blue: 0.82), Color(red: 0.79, green: 0.85, blue: 0.97), Color(red: 0.96, green: 0.85, blue: 0.71),
                                 Color(red: 0.90, green: 0.81, blue: 0.94), Color(red: 0.96, green: 0.79, blue: 0.81), Color(red: 0.84, green: 0.90, blue: 0.66)]
}

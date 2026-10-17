import SwiftUI
import Shared

/// The iPhone app: all screens are the shared Compose UI (shared/src/commonMain), shown through one view controller. SwiftUI only owns the window.
@main
struct ExpensesApp: App {
    init() { NotificationHost.start(CryptoServices()) }

    var body: some Scene {
        WindowGroup {
            SharedRoot()
                // Compose lays itself out under the status bar, the home indicator and the keyboard and applies those insets itself.
                .ignoresSafeArea(.all)
        }
    }
}

struct SharedRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(host: CryptoServices())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

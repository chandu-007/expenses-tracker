import UIKit
import UniformTypeIdentifiers

/// "Share to Expenses": the Share sheet of Messages, Mail or any app can hand a bank text or email to the app without a Shortcuts automation. A share extension
/// cannot open the app's database (it runs apart, briefly), so the words are put in the App Group's preferences and the app reads them the next time it comes forward
/// (Kotlin, IosSharedTexts), judging them exactly as a pasted text: a bank transaction goes to To review, anything else is thrown away.
private let group = "group.app.expenses.tracker.ios"
private let queueKey = "shared_texts"
private let maxQueued = 20
private let maxLength = 4000

final class ShareViewController: UIViewController {
    private let label = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        label.text = "Reading…"
        label.numberOfLines = 0
        label.textAlignment = .center
        label.font = .preferredFont(forTextStyle: .headline)
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
        ])
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        collectText { [weak self] text in
            DispatchQueue.main.async {
                guard let self else { return }
                if let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Self.enqueue(String(text.prefix(maxLength)))
                    self.label.text = "Sent to Expenses.\nOpen the app and look in To review."
                } else {
                    self.label.text = "There was no text to read."
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { self.extensionContext?.completeRequest(returningItems: nil) }
            }
        }
    }

    /// The text of what was shared: plain text first, else the item's own words.
    private func collectText(_ done: @escaping (String?) -> Void) {
        let items = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        for item in items {
            for provider in item.attachments ?? [] where provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { value, _ in
                    done((value as? String) ?? (value as? NSAttributedString)?.string)
                }
                return
            }
        }
        done(items.compactMap { $0.attributedContentText?.string }.first)
    }

    static func enqueue(_ text: String) {
        guard let d = UserDefaults(suiteName: group) else { return }
        var queue = d.stringArray(forKey: queueKey) ?? []
        queue.append(text)
        d.set(Array(queue.suffix(maxQueued)), forKey: queueKey)
    }
}

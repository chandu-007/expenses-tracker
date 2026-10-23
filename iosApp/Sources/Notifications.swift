import UIKit
import UserNotifications
import BackgroundTasks
import Shared

/// Reminders on the iPhone. The decisions are Kotlin (`IosNotifications`: the same check Android runs every hour, then local notifications);
/// this file is only what must be Swift or must happen at launch: the notification delegate (banner while the app is open, tap handling), the
/// background refresh task, and the lifecycle hooks that run the check. `NotificationHost.start(...)` is called once from `ExpensesApp.init`.
///
/// iOS decides when a `BGAppRefreshTask` runs (rarely for an app that is seldom opened, never in Low Power Mode or when the person turned
/// Background App Refresh off); reminders for the next days are therefore also handed to the system ahead of time by the Kotlin check.
enum NotificationHost {
    static let refreshIdentifier = "app.expenses.tracker.ios.refresh"
    private static var started = false
    private static let delegate = NotificationDelegate()

    static func start(_ host: IosHostServices) {
        guard !started else { return }
        started = true
        IosNotifications.shared.install(host: host)
        UNUserNotificationCenter.current().delegate = delegate

        // Must be registered before the app has finished launching.
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshIdentifier, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            handle(task)
        }
        scheduleRefresh()

        let center = NotificationCenter.default
        center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in IosNotifications.shared.onForeground() }
        center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in leftForeground() }

        if ProcessInfo.processInfo.arguments.contains("-notify-debug-run") { runDebug() }
    }

    /// The next background refresh, no earlier than an hour from now (the system picks the actual time, or none).
    static func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handle(_ task: BGAppRefreshTask) {
        scheduleRefresh()
        task.expirationHandler = { IosNotifications.shared.cancelCheck() }
        IosNotifications.shared.checkAsync { _ in task.setTaskCompleted(success: true) }
    }

    /// Checks once more as the app goes away (what changed while it was open: a budget crossed, a bill posted so its pending reminder must go),
    /// and asks the system for a refresh. A short background task lets the check finish.
    private static func leftForeground() {
        scheduleRefresh()
        var id = UIBackgroundTaskIdentifier.invalid
        id = UIApplication.shared.beginBackgroundTask(withName: "reminder-check") {
            IosNotifications.shared.cancelCheck()
            UIApplication.shared.endBackgroundTask(id)
        }
        IosNotifications.shared.checkAsync { _ in
            DispatchQueue.main.async { if id != .invalid { UIApplication.shared.endBackgroundTask(id) } }
        }
    }

    // MARK: UI tests

    /// Launch argument `-notify-debug-run` (UI tests only): Kotlin adds two made-up bills, runs the check and reports what the system holds; the
    /// report is shown as an accessibility label `notify_debug` so the test can read it.
    private static func runDebug() {
        IosNotifications.shared.debugSeedAndCheck { report in
            DispatchQueue.main.async { showDebug(report) }
        }
    }

    private static func showDebug(_ report: String) {
        let window = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap { $0.windows }.first
        guard let window else { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { showDebug(report) }; return }
        let label = UILabel(frame: CGRect(x: 0, y: 50, width: 200, height: 14))
        label.font = .systemFont(ofSize: 6)
        label.textColor = .clear
        label.text = report
        label.isAccessibilityElement = true
        label.accessibilityIdentifier = "notify_debug"
        label.accessibilityLabel = report
        window.addSubview(label)
    }
}

/// Shows a notification as a banner even while the app is open (Android does too), and opens the app on a tap.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let request = response.notification.request
        let action = response.actionIdentifier
        if action == UNNotificationDefaultActionIdentifier || action == UNNotificationDismissActionIdentifier {
            IosNotifications.shared.opened(key: request.identifier)
            completionHandler()
            return
        }
        // a button on the notification: Mark paid, the two snoozes, Pay now
        let info = request.content.userInfo
        IosNotifications.shared.performAction(action: action, key: request.identifier, ref: info["ref"] as? String, kind: (info["kind"] as? String) ?? "",
                                              title: request.content.title, body: request.content.body) { completionHandler() }
    }
}

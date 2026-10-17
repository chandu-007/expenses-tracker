package app.expenses

/** Capture sources and the settings keys the capture screens read and write. */
object CaptureKeys {
    const val SMS = "SMS"
    const val NOTIFICATION = "NOTIFICATION"
    const val SETTING_NOTIF_ON = "capture_notif_on"
    const val SETTING_EXTRA_ROOTS = "capture_extra_roots"
    const val SETTING_SMS_ON = "capture_sms_on"
}

/** Settings keys and constants of automatic export. */
object AutoExportKeys {
    const val KEEP = 7
    const val BACKUP_PREFIX = "expenses-backup"
    const val XLSX_PREFIX = "expenses"
    const val FOLDER = "auto_export_folder"
    const val FREQ = "auto_export_freq"
    const val LAST_AT = "auto_export_last_at"
    const val STATUS = "auto_export_status"
}

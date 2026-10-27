package app.expenses

/** Capture sources and the settings keys the capture screens read and write. */
object CaptureKeys {
    const val SMS = "SMS"
    const val NOTIFICATION = "NOTIFICATION"
    const val SETTING_NOTIF_ON = "capture_notif_on"
    const val SETTING_EXTRA_ROOTS = "capture_extra_roots"
    const val SETTING_SMS_ON = "capture_sms_on"
    const val SETTING_EMAIL_ON = "capture_email_on"
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

/** Settings keys and limits for what a backup carries. */
object BackupKeys {
    /** "0" when the person switched photos and files off for backups; anything else (including nothing) means they are included. Applies to manual and automatic backups. */
    const val INCLUDE_FILES = "backup_include_files"

    /** Automatic copies leave photos and files out when together they are over this size: the newest [AutoExportKeys.KEEP] copies are kept, each with its own copy of them. */
    const val AUTO_FILES_LIMIT_BYTES = 100L * 1024 * 1024

    /** "Send backup to..." builds the file in memory for the share sheet, which also has limits of its own (email takes about 25 MB): over this size the files are left out there. */
    const val SHARE_FILES_LIMIT_BYTES = 25L * 1024 * 1024
}

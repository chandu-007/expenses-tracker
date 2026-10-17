package app.expenses

/** Where the guide remembers what the person has done with it. */
object GuideSettings {
    /** The guide version last finished or skipped. Empty or missing: never seen. */
    const val SEEN = "guide_seen"
    /** "1": do not offer the guide after updates. A first visit is still offered. */
    const val OFF = "guide_off"
}

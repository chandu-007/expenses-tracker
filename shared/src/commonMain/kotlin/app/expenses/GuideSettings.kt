package app.expenses

/** Where the guide remembers what the person has done with it. */
object GuideSettings {
    /** The guide version last finished, skipped or passed by. Empty or missing: a first install, which is recorded as having seen the current version when the app first opens (the tour is opt-in there). */
    const val SEEN = "guide_seen"
    /** "1": do not offer the guide after updates. */
    const val OFF = "guide_off"
}

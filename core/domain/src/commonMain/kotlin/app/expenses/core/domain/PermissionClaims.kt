package app.expenses.core.domain

/**
 * What the "Verify it yourself" page claims about the app's permissions, worked out from the permissions the installed app really declares (Android reads its own manifest),
 * so the page can only say a thing is not requested when the manifest does not list it. Nothing here is a fixed claim: a build whose manifest lists one of these permissions
 * moves that kind of access from "not requested" to "requested" on its own.
 */
object PermissionClaims {
    /** One kind of sensitive access, named for a person, and every Android permission that gives it. */
    class Claim(val id: String, val label: String, val permissions: List<String>)

    /** The kinds of access checked, in the order the page lists them. */
    val SENSITIVE: List<Claim> = listOf(
        Claim("internet", "Internet", listOf("android.permission.INTERNET")),
        Claim("contacts", "Contacts", listOf("android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.GET_ACCOUNTS")),
        Claim("location", "Location", listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION")),
        Claim("microphone", "Microphone", listOf("android.permission.RECORD_AUDIO")),
        Claim("camera", "Camera", listOf("android.permission.CAMERA")),
        Claim("phone", "Phone and call log", listOf(
            "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS", "android.permission.CALL_PHONE", "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG", "android.permission.ANSWER_PHONE_CALLS",
        )),
        Claim("files", "All your files and photos", listOf(
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        )),
        Claim("texts", "Your text messages", listOf(
            "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS", "android.permission.RECEIVE_MMS", "android.permission.RECEIVE_WAP_PUSH",
        )),
    )

    /** [notRequested]: the kinds none of whose permissions are declared. [requested]: the kinds at least one permission of which is declared. Together they are [SENSITIVE], in its order. */
    class Result(val notRequested: List<Claim>, val requested: List<Claim>)

    /** Sorts [SENSITIVE] by whether any of [declared] (full permission names, as the manifest lists them) belongs to it. */
    fun of(declared: List<String>): Result {
        val have = declared.toSet()
        val (asked, notAsked) = SENSITIVE.partition { c -> c.permissions.any { it in have } }
        return Result(notAsked, asked)
    }

    /** The sentence under the list: how many of the kinds are not asked for, in words a person can check. */
    fun summary(result: Result): String {
        val total = result.notRequested.size + result.requested.size
        return when {
            result.requested.isEmpty() -> "This build asks for none of the $total kinds of access checked here."
            result.notRequested.isEmpty() -> "This build asks for all $total kinds of access checked here."
            else -> "Of $total kinds of access checked here, this build asks for ${result.requested.size} (${result.requested.joinToString { it.label.replaceFirstChar { c -> c.lowercase() } }}) and not for the other ${result.notRequested.size}."
        }
    }
}

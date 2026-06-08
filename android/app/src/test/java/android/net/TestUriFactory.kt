package android.net

import android.os.Parcel

/**
 * JVM 本地单测里不能直接调用 Uri.parse，
 * 这里在 android.net 同包下提供一个最小可用实现，专门给测试假数据使用。
 */
fun createTestUri(rawValue: String = "content://test/item"): Uri {
    val scheme = rawValue.substringBefore("://", "")
    val schemeSpecificPart = if (scheme.isBlank()) {
        rawValue
    } else {
        rawValue.substringAfter("://", "")
    }
    val authority = schemeSpecificPart.substringBefore('/', "")
    val pathWithoutQuery = schemeSpecificPart.substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
    val path = if (pathWithoutQuery.isBlank()) "" else "/$pathWithoutQuery"
    val pathSegments = pathWithoutQuery.split('/')
        .filter { it.isNotBlank() }
    val query = rawValue.substringAfter('?', "")
        .substringBefore('#')
        .ifBlank { null }
    val fragment = rawValue.substringAfter('#', "")
        .ifBlank { null }

    return object : Uri() {
        override fun buildUpon(): Builder {
            throw UnsupportedOperationException("测试里不会调用 buildUpon")
        }

        override fun getAuthority(): String = authority

        override fun getEncodedAuthority(): String = authority

        override fun getEncodedFragment(): String? = fragment

        override fun getEncodedPath(): String = path

        override fun getEncodedQuery(): String? = query

        override fun getEncodedSchemeSpecificPart(): String = schemeSpecificPart

        override fun getEncodedUserInfo(): String? = null

        override fun getFragment(): String? = fragment

        override fun getHost(): String = authority

        override fun getLastPathSegment(): String? = pathSegments.lastOrNull()

        override fun getPath(): String = path

        override fun getPathSegments(): MutableList<String> = pathSegments.toMutableList()

        override fun getPort(): Int = -1

        override fun getQuery(): String? = query

        override fun getScheme(): String? = scheme.ifBlank { null }

        override fun getSchemeSpecificPart(): String = schemeSpecificPart

        override fun getUserInfo(): String? = null

        override fun isHierarchical(): Boolean = true

        override fun isRelative(): Boolean = scheme.isBlank()

        override fun toString(): String = rawValue

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }
}

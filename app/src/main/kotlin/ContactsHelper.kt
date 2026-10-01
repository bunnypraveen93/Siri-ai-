package com.praveen.siriai

import android.content.Context
import android.provider.ContactsContract

/** Looks up a phone contact by (partial) name so calls can be placed by saying a name. */
object ContactsHelper {
    data class Contact(val name: String, val number: String)

    fun findContact(context: Context, query: String): Contact? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = try {
            resolver.query(uri, projection, null, null, null)
        } catch (e: SecurityException) {
            null
        } ?: return null

        var partialMatch: Contact? = null
        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx) ?: continue
                if (name.equals(query, ignoreCase = true)) {
                    return Contact(name, number) // exact match — return immediately
                }
                if (partialMatch == null && name.contains(query, ignoreCase = true)) {
                    partialMatch = Contact(name, number)
                }
            }
        }
        return partialMatch
    }
}

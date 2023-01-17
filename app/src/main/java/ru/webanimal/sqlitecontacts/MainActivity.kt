package ru.webanimal.sqlitecontacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.webanimal.sqlitecontacts.databinding.ActivityMainBinding

@OptIn(FlowPreview::class)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val contactsFlow = MutableSharedFlow<List<String>>(replay = 1)
    private val fetchedData = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactsRv.adapter = ContactsAdapter()

        logIfHasPermissions { fetchData() }
        lifecycleScope.launchWhenResumed {
            searchFlow().onEach { logFilteredIfHasPermissions(it) }
                .launchIn(scope = this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED
            && grantResults[1] == PackageManager.PERMISSION_GRANTED
        ) {
            fetchData()

        } else {
            Toast.makeText(this, getString(R.string.main_permissions_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun logFilteredIfHasPermissions(input: String) {
        logIfHasPermissions {
            (binding.contactsRv.adapter as ContactsAdapter).submitList(filteredContacts(input))
        }
    }

    private fun filteredContacts(input: String): List<String> {
        return if (input.isEmpty()) {
            fetchedData

        } else {
            fetchedData.filter {
                it.contains(input, ignoreCase = true)
            }
        }
    }

    private fun logIfHasPermissions(action: () -> Unit) {
        val isReadGranted =
            checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val isWriteGranted =
            checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

        if (isReadGranted && isWriteGranted) {
            action()

        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                ),
                PERMISSIONS_REQUEST_CODE,
            )
        }
    }

    private fun fetchData() {
        lifecycleScope.launch {
            getContacts().flowOn(Dispatchers.IO)
                .collect {
                    (binding.contactsRv.adapter as ContactsAdapter).submitList(it)
                }
        }
    }

    private suspend fun getContacts(): Flow<List<String>> {
        val cursor = Query(
            uri = ContactsContract.Data.CONTENT_URI,
            projection = CONTACTS_PROJECTION,
            selection = " (" + ContactsContract.Data.MIMETYPE
                    + " = ? OR " + ContactsContract.Data.MIMETYPE
                    + " = ?) AND " + ContactsContract.Data.HAS_PHONE_NUMBER + " = 1",
            selectionArgs = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ),
            orderBy = ContactsContract.Contacts.DISPLAY_NAME + " ASC "
        ).openCursor(this@MainActivity.applicationContext)

        fetchedData.clear()
        fetchedData.addAll(
            if (cursor != null) {
                getSortedContactsFromCursor(cursor)
            } else {
                emptyList()
            }
        )
        contactsFlow.emit(fetchedData)
        return contactsFlow
    }

    private fun getSortedContactsFromCursor(cursor: Cursor): List<String> {
        val contacts = mutableListOf<String>()
        val sb = StringBuilder()
        cursor.columnNames?.forEach {
            sb.append(it)
            sb.append("; ")
        }
        val item = "Column names: $sb.toString()"
        Log.d("TEST::", item)
        contacts.add(item)

        if (cursor.moveToFirst()) {
            do {
                val localSb = StringBuilder()
                val columnsQty = cursor.columnCount
                for (idx in 0 until columnsQty) {
                    localSb.append(cursor.getString(idx))
                    if (idx < columnsQty - 1) localSb.append("; ")
                }
                val localItem = String.format("Row: %d, Values: %s", cursor.position, localSb.toString())
                Log.d("TEST::", localItem)
                contacts.add(localItem)
            } while (cursor.moveToNext())
        }
        cursor.close()

        return contacts
    }

    private fun searchFlow(): Flow<String> {
        return callbackFlow {
            val listener = object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    // not used
                }

                override fun onTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    text?.let { trySend(it.toString()) }
                }

                override fun afterTextChanged(p0: Editable?) {
                    // not used
                }
            }

            binding.searchInputEt.addTextChangedListener(listener)
            awaitClose { binding.searchInputEt.removeTextChangedListener(listener) }
        }.debounce(DATE_CHANGE_LISTENER_DELAY_TIME)
            .distinctUntilChanged()
    }

    private fun Query.openCursor(context: Context): Cursor? {
        val contentResolver = context.contentResolver
        return contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            orderBy
        )
    }

    companion object {

        private const val PERMISSIONS_REQUEST_CODE = 123

        private const val DATE_CHANGE_LISTENER_DELAY_TIME = 200L

        private val CONTACTS_PROJECTION = arrayOf(
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.DATA_VERSION,
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.Photo.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Data.LOOKUP_KEY
        )
    }
}
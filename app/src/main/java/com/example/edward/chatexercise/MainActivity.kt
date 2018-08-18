package com.example.edward.chatexercise

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    private var mUserName = ANONYMOUS
    private lateinit var mMessageAdapter: MessageAdapter

    private lateinit var mFirebaseDatabase: FirebaseDatabase
    private lateinit var mFirebaseDatabaseReference: DatabaseReference
    private var mChildEventListener: ChildEventListener? = null
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mAuthStateListener: FirebaseAuth.AuthStateListener
    private lateinit var mFirebaseStorage: FirebaseStorage
    private lateinit var mFirebaseStorageReference: StorageReference
    private lateinit var mFirebaseRemoteConfig: FirebaseRemoteConfig


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val friendlyMessages = arrayListOf<FriendlyMessage>()
        mMessageAdapter = MessageAdapter(this, R.layout.item_message, friendlyMessages)
        messageListView.adapter = mMessageAdapter
        progressBar.visibility = View.INVISIBLE
        photoPickerButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
                    .setType("image/jpeg")
                    .putExtra(Intent.EXTRA_LOCAL_ONLY, true)

            startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER)

        }

        messageEditText.addTextChangedListener(object : TextWatcher {

            override fun onTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {
                sendButton.isEnabled = charSequence.toString().trim().isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable?) {}

            override fun beforeTextChanged(charSequence: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        messageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))

        sendButton.setOnClickListener {
            val friendlyMessage = FriendlyMessage(messageEditText.text.toString(), mUserName, null)
            mFirebaseDatabaseReference.push().setValue(friendlyMessage)

            // clear input box
            messageEditText.text.clear()
        }

        mFirebaseDatabase = FirebaseDatabase.getInstance()
        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseStorage = FirebaseStorage.getInstance()
        mFirebaseDatabaseReference = mFirebaseDatabase.reference.child("messages")
        mFirebaseStorageReference = mFirebaseStorage.reference.child("chat_pictures")

        mAuthStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Toast.makeText(this@MainActivity,
                        "Welcome ${user.displayName} to Chat", Toast.LENGTH_SHORT).show()
                onSignedInInitialize(user.displayName)
            } else {
                onSignedOutTearDown()
                val providers = Arrays.asList(
                        AuthUI.IdpConfig.EmailBuilder().build(),
                        AuthUI.IdpConfig.GoogleBuilder().build())
                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setAvailableProviders(providers)
                                .setIsSmartLockEnabled(false)
                                .build(),
                        RC_SIGN_IN)
            }
        }
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build()
        mFirebaseRemoteConfig.setConfigSettings(configSettings)
        val defaultConfigMap: MutableMap<String, Any> = HashMap()
        defaultConfigMap[MSG_LENGTH_KEY] = DEFAULT_MSG_LENGTH_LIMIT
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap)
        fetchConfig()
    }

    private fun fetchConfig(){
        var cacheExpiration: Long = 3600
        if (mFirebaseRemoteConfig.info.configSettings.isDeveloperModeEnabled)
            cacheExpiration = 0

        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener {
                    mFirebaseRemoteConfig.activateFetched()
                    applyRetrieveLengthLimit()
                }
                .addOnFailureListener {
                    Log.d(TAG_MAIN, "Error fetching config ${it.message}")
                    applyRetrieveLengthLimit()
                }
    }

    private fun applyRetrieveLengthLimit(){
        val msgLength: Long = mFirebaseRemoteConfig.getLong(MSG_LENGTH_KEY)
        messageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(msgLength.toInt()))
        Log.d(TAG_MAIN, "$MSG_LENGTH_KEY = $msgLength")
    }

    private fun onSignedInInitialize(username: String?) {
        mUserName = username ?: ANONYMOUS
        attachDatabaseMessageListener()
    }

    private fun onSignedOutTearDown() {
        mUserName = ANONYMOUS
        mMessageAdapter.clear()
        detachDatabaseMessageListener()
    }

    private fun attachDatabaseMessageListener() {
        mChildEventListener = object : ChildEventListener {

            override fun onChildAdded(dataSnapshot: DataSnapshot, p1: String?) {
                val friendlyMessage = dataSnapshot.getValue(FriendlyMessage::class.java)
                mMessageAdapter.add(friendlyMessage)
            }

            override fun onCancelled(p0: DatabaseError) {}
            override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
            override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {}
            override fun onChildRemoved(p0: DataSnapshot) {}
        }
        mFirebaseDatabaseReference.addChildEventListener(mChildEventListener!!)
    }

    private fun detachDatabaseMessageListener() {
        mChildEventListener?.let {
            mFirebaseDatabaseReference.removeEventListener(mChildEventListener!!)
            mChildEventListener = null
        }
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAuth.addAuthStateListener(mAuthStateListener)
    }

    override fun onPause() {
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener)
        detachDatabaseMessageListener()
        mMessageAdapter.clear()
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?) =
            when (item?.itemId) {
            R.id.sign_out_menu -> {
                AuthUI.getInstance().signOut(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_SIGN_IN && resultCode == Activity.RESULT_CANCELED){
            finish()
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK && data != null){
            val selectedImageUri: Uri = data.data
            val photRef = mFirebaseStorageReference.child(selectedImageUri.lastPathSegment)
            val uploadTask = photRef.putFile(selectedImageUri)
            val uriTask = uploadTask.continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
                if (!task.isSuccessful) throw task.exception as Throwable
                else  photRef.downloadUrl
            }).addOnCompleteListener {task ->
                if (task.isSuccessful){
                    val downloadUri = task.result
                    mFirebaseDatabaseReference.push().setValue(FriendlyMessage(null, mUserName, downloadUri.toString()))
                } else {
                    Toast.makeText(this, "getting downloadUri failed.", Toast.LENGTH_SHORT).show()
                }
            }

        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

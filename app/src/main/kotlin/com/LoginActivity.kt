package com.praveen.siriai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account?.idToken != null) {
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                Toast.makeText(this, "Sign in failed: Token null", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Log.w("GoogleSignIn", "Failed code: ${e.statusCode}")
            Toast.makeText(this, "Sign in failed code: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e("GoogleSignIn", "Launcher Error: ${t.message}", t)
            Toast.makeText(this, "Launcher Error: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("1092066177158-lio3k99kr8fh95gi6g2ri2q39va7v2uq.apps.googleusercontent.com")
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        val signInButton = findViewById<Button>(R.id.signInButton)
        signInButton.setOnClickListener {
            try {
                // ఎర్రర్ రాకుండా ఇక్కడ getSignInIntent() వాడటం జరిగింది
                val signInIntent = mGoogleSignInClient.getSignInIntent()
                googleSignInLauncher.launch(signInIntent)
            } catch (t: Throwable) {
                Log.e("LoginActivity", "Crash Caught: ${t.message}", t)
                Toast.makeText(this@LoginActivity, "Crash Error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Toast.makeText(this, "Welcome " + (user?.displayName ?: "User"), Toast.LENGTH_LONG).show()
                } else {
                    Log.w("FirebaseAuth", "Failure", task.exception)
                    Toast.makeText(this, "Auth Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

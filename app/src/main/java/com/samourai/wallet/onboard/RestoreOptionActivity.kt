package com.samourai.wallet.onboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.samourai.wallet.R
import com.samourai.wallet.RestoreSeedWalletActivity
import com.samourai.wallet.access.AccessFactory
import com.samourai.wallet.crypto.AESUtil
import com.samourai.wallet.hd.HD_WalletFactory
import com.samourai.wallet.payload.ExternalBackupManager
import com.samourai.wallet.payload.PayloadUtil
import com.samourai.wallet.util.AppUtil
import com.samourai.wallet.util.CharSequenceX
import com.samourai.wallet.util.PrefsUtil
import kotlinx.android.synthetic.main.activity_restore_option.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*


class RestoreOptionActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restore_option)
        window.statusBarColor = ContextCompat.getColor(this, R.color.window)
        setSupportActionBar(restoreOptionToolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = ""

        samouraiMnemonicRestore.setOnClickListener {
            val intent = Intent(this@RestoreOptionActivity, RestoreSeedWalletActivity::class.java)
            intent.putExtra("mode", "mnemonic")
            startActivity(intent)
        }
        samouraiBackupFileRestore.setOnClickListener {
            val intent = Intent(this@RestoreOptionActivity, RestoreSeedWalletActivity::class.java)
            intent.putExtra("mode", "backup")
            startActivity(intent)
        }
        externalWalletRestore.setOnClickListener {
            val intent = Intent(this@RestoreOptionActivity, RestoreSeedWalletActivity::class.java)
            intent.putExtra("mode", "mnemonic")
            startActivity(intent)
        }

        restoreBtn.setOnClickListener { restoreWalletFromBackup() }

        ExternalBackupManager.getPermissionStateLiveData().observe(this, Observer {
            if (ExternalBackupManager.backupAvailable()) {
                restoreFromBackupSnackbar.visibility = View.VISIBLE
            } else {
                restoreFromBackupSnackbar.visibility = View.GONE
            }
        })

        if (ExternalBackupManager.backupAvailable()) {
            restoreFromBackupSnackbar.visibility = View.VISIBLE
        } else {
            restoreFromBackupSnackbar.visibility = View.GONE
        }

        restoreOptionToolBar.setNavigationOnClickListener {
            this.onBackPressed()
        }

    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            loaderRestore.visibility = View.VISIBLE
            restoreBtn.visibility = View.GONE
        } else {
            loaderRestore.visibility = View.GONE
            restoreBtn.visibility = View.VISIBLE
        }
    }

    private fun restoreWalletFromBackup() {


        fun initializeRestore(decrypted: String) {
            val json = JSONObject(decrypted)
            val hdw = PayloadUtil.getInstance(applicationContext).restoreWalletfromJSON(json)
            HD_WalletFactory.getInstance(applicationContext).set(hdw)
            val guid = AccessFactory.getInstance(applicationContext).createGUID()
            val hash = AccessFactory.getInstance(applicationContext).getHash(guid, CharSequenceX(AccessFactory.getInstance(applicationContext).pin), AESUtil.DefaultPBKDF2Iterations)
            PrefsUtil.getInstance(applicationContext).setValue(PrefsUtil.ACCESS_HASH, hash)
            PrefsUtil.getInstance(applicationContext).setValue(PrefsUtil.ACCESS_HASH2, hash)
            PayloadUtil.getInstance(applicationContext).saveWalletToJSON(CharSequenceX(guid + AccessFactory.getInstance().pin))
        }

        suspend fun readBackUp(password: String) = withContext(Dispatchers.IO) {
            try {
                val backupData = ExternalBackupManager.read()
                if (backupData != null) {
                    val decrypted = PayloadUtil.getInstance(applicationContext).getDecryptedBackupPayload(backupData, CharSequenceX(password))
                    if (decrypted != null && decrypted.isNotEmpty()) {
                        val job = async(Dispatchers.IO) {
                            initializeRestore(decrypted)
                        }
                        job.invokeOnCompletion {
                            it?.let {
                                throw it
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw CancellationException(e.message)
            }
        }

        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.restore_backup)
        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.password_input_dialog_layout, null)
        view.findViewById<TextView>(R.id.dialogMessage).setText(R.string.enter_your_wallet_passphrase)
        val password = view.findViewById<EditText>(R.id.restore_dialog_password_edittext)
        builder.setView(view)
        builder.setPositiveButton(R.string.restore) { dialog, which ->
            dialog.dismiss()
            showLoading(true)
            scope.launch {
                readBackUp(password.text.toString())
            }.invokeOnCompletion {
                it?.printStackTrace()
                if (it != null) {
                    scope.launch(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(this@RestoreOptionActivity, R.string.decryption_error, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    AppUtil.getInstance(this@RestoreOptionActivity).restartApp()

                }

            }
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
        builder.create().show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.get_help_menu_create -> {
                doSupportCreate()
                false
            }
            R.id.get_help_menu_restore -> {
                doSupportRestore()
                false
            }
            else -> {
                false
            }
        }

    }


    private fun doSupportCreate() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://samourai.kayako.com/section/1-starting-a-new-wallet"))
        startActivity(intent)
    }

    private fun doSupportRestore() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://samourai.kayako.com/category/3-restore-recovery"))
        startActivity(intent)
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.landing_activity_menu, menu)
        return true
    }
}
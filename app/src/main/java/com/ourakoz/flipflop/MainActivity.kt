package com.ourakoz.flipflop

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.dialog_phone.view.*
import kotlinx.android.synthetic.main.dialog_phone.view.et_name
import kotlinx.android.synthetic.main.dialog_sms.view.*
import kotlinx.android.synthetic.main.dialog_type.view.*
import kotlinx.android.synthetic.main.dialog_url.view.*
import kotlinx.coroutines.*


@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val PHONE_COMMAND_CALL_PHONE_PERMISSION = 23764
    private val SMS_COMMAND_SMS_PERMISSION = 18634
    private var nfcAdapter : NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null
    lateinit var db: FlipflopDatabase
    lateinit var commands: List<Command>
    private var currentPhoneNumber = ""
    private var currentMessage = ""
    private var currentUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        db = Room.databaseBuilder(
            applicationContext,
            FlipflopDatabase::class.java, "flipflop-commands"
        ).build()

        launch (Dispatchers.IO)  {
            commands = db.commandDao().getAll()
            runOnUiThread {
                rv_commands.layoutManager = LinearLayoutManager(applicationContext)
                rv_commands.adapter = CommandsAdapter(commands, applicationContext)
            }
        }

        //Retrieve NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcPendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)

        // Activity started from NFC intent
        if (intent != null) processIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        // Start NFC scan
        nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null);
    }

    override fun onPause() {
        super.onPause()

        // Stop NFC scan
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        // NFC intent detected during Activity life
        if (intent != null) processIntent(intent)
    }

    private fun processIntent(intentToProcess: Intent) {
        if (intentToProcess.action == NfcAdapter.ACTION_NDEF_DISCOVERED || intentToProcess.action == NfcAdapter.ACTION_TAG_DISCOVERED){
            launch(Dispatchers.IO) {
                val id = intentToProcess.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG).id.toHexString()
                val command = db.commandDao().findByNfcId(id)
                if (command != null) {
                    runOnUiThread {
                        when (command.type) {
                            CommandType.PHONE.toString() -> {
                                currentPhoneNumber = command.phoneNumber
                                if (ContextCompat.checkSelfPermission(this@MainActivity,
                                        Manifest.permission.CALL_PHONE)
                                    != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(this@MainActivity,
                                        arrayOf(Manifest.permission.CALL_PHONE), PHONE_COMMAND_CALL_PHONE_PERMISSION)
                                } else phoneCall()
                            }
                            CommandType.SMS.toString() -> {
                                currentPhoneNumber = command.phoneNumber
                                currentMessage = command.message
                                if (ContextCompat.checkSelfPermission(this@MainActivity,
                                        Manifest.permission.SEND_SMS)
                                    != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(this@MainActivity,
                                        arrayOf(Manifest.permission.SEND_SMS), SMS_COMMAND_SMS_PERMISSION)
                                } else sendSms()
                            }
                            CommandType.URL.toString() -> {
                                currentUrl = command.url
                                openUrl()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        displayTypeDialog(id)
                    }
                }
            }
        }
    }

    private fun displayTypeDialog(nfcId: String) {
        val dialogBuilder = AlertDialog.Builder(this@MainActivity)
        val view = layoutInflater.inflate(R.layout.dialog_type, null)
        dialogBuilder.setView(view)
        dialogBuilder.setTitle(R.string.add_new_cmd)
        dialogBuilder.setCancelable(true)
        dialogBuilder.setNegativeButton(R.string.cancel, { dialog, _ ->  dialog.dismiss() } )
        val dialog = dialogBuilder.create()
        view.phone_btn.setOnClickListener {
            dialog.dismiss()
            displayPhoneDialog(nfcId)
        }
        view.sms_btn.setOnClickListener {
            dialog.dismiss()
            displaySmsDialog(nfcId)
        }
        view.url_btn.setOnClickListener {
            dialog.dismiss()
            displayUrlDialog(nfcId)
        }
        dialog.show()
    }

    private fun displayPhoneDialog(nfcId: String) {
        val dialogBuilder = AlertDialog.Builder(this@MainActivity)
        val view = layoutInflater.inflate(R.layout.dialog_phone, null)
        dialogBuilder.setView(view)
        dialogBuilder.setTitle(R.string.add_phone_cmd)
        dialogBuilder.setCancelable(true)
        dialogBuilder.setNegativeButton(R.string.cancel, { dialog, _ ->  dialog.dismiss() } )
        dialogBuilder.setPositiveButton(R.string.ok) { _, _ -> saveCommand(nfcId, view.et_name.text.toString(), CommandType.PHONE, phoneNumber = view.et_phone_number.text.toString()) }
        dialogBuilder.create().show()
    }

    private fun displaySmsDialog(nfcId: String) {
        val dialogBuilder = AlertDialog.Builder(this@MainActivity)
        val view = layoutInflater.inflate(R.layout.dialog_sms, null)
        dialogBuilder.setView(view)
        dialogBuilder.setTitle(R.string.add_sms_cmd)
        dialogBuilder.setCancelable(true)
        dialogBuilder.setNegativeButton(R.string.cancel, { dialog, _ ->  dialog.dismiss() } )
        dialogBuilder.setPositiveButton(R.string.ok) { _, _ ->
            saveCommand(nfcId, view.et_name.text.toString(), CommandType.SMS, phoneNumber = view.et_sms_number.text.toString(), message = view.et_message.text.toString())
        }
        dialogBuilder.create().show()
    }

    private fun displayUrlDialog(nfcId: String) {
        val dialogBuilder = AlertDialog.Builder(this@MainActivity)
        val view = layoutInflater.inflate(R.layout.dialog_url, null)
        dialogBuilder.setView(view)
        dialogBuilder.setTitle(R.string.add_url_cmd)
        dialogBuilder.setCancelable(true)
        dialogBuilder.setNegativeButton(R.string.cancel, { dialog, _ ->  dialog.dismiss() } )
        dialogBuilder.setPositiveButton(R.string.ok) { _, _ -> saveCommand(nfcId, view.et_name.text.toString(), CommandType.URL, url = view.et_url.text.toString()) }
        dialogBuilder.create().show()
    }

    private fun phoneCall() {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + currentPhoneNumber))
        startActivity(intent)
    }

    private fun sendSms() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + currentPhoneNumber))
            .putExtra("sms_body", currentMessage)
        startActivity(intent)
    }


    private fun openUrl() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
        startActivity(intent)
    }

    @SuppressLint("WrongConstant")
    private fun saveCommand(nfcId: String, name: String, type: CommandType, phoneNumber: String = "", message: String = "", url: String = "") {
        launch(Dispatchers.IO) {
            db.commandDao().insertAll(
                Command (nfcId = nfcId, name = name, type = type.toString(), phoneNumber = phoneNumber, message = message, url = url)
            )
            commands = db.commandDao().getAll()
            runOnUiThread {
                rv_commands.adapter?.notifyDataSetChanged()
                Toast.makeText(this@MainActivity, R.string.new_cmd_added, 8).show()
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        var buffer = StringBuffer()
        this.forEach { b -> buffer.append(String.format("%02X", b)) }
        return buffer.toString()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PHONE_COMMAND_CALL_PHONE_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) phoneCall()
                return
            }
            SMS_COMMAND_SMS_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) sendSms()
                return
            }
        }
    }
}

@Entity
data class Command(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "nfc_id") val nfcId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String = "",
    @ColumnInfo(name = "message") val message: String = "",
    @ColumnInfo(name = "url") val url: String = ""
)

@Dao
interface CommandDao {
    @Query("SELECT * FROM command")
    fun getAll(): List<Command>

    @Query("SELECT * FROM command WHERE uid IN (:commandIds)")
    fun loadAllByIds(commandIds: IntArray): List<Command>

    @Query("SELECT * FROM command WHERE nfc_id LIKE :nfcId LIMIT 1")
    fun findByNfcId(nfcId: String): Command

    @Insert
    fun insertAll(vararg commands: Command)

    @Delete
    fun delete(command: Command)

    @Query("DELETE FROM command")
    fun nukeTable()
}

@Database(entities = arrayOf(Command::class), version = 1)
abstract class FlipflopDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao
}

enum class CommandType(val type: String) {
    PHONE("phone"),
    SMS("sms"),
    URL("url")
}

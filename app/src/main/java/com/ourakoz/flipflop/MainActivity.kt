package com.ourakoz.flipflop

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.*


class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private var nfcAdapter : NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null
    lateinit var db: FlipflopDatabase
    lateinit var commands: List<Command>

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
                if (db.commandDao().findByNfcId(id) != null) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Appel de Titi en cours", Toast.LENGTH_LONG).show()
                    }
                } else {
                    db.commandDao().insertAll(
                        Command (1, id, "Appeler Titi", CommandType.PHONE.toString())
                    )
                    commands = db.commandDao().getAll()
                    runOnUiThread {
                        rv_commands.adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        var buffer = StringBuffer()
        this.forEach { b -> buffer.append(String.format("%02X", b)) }
        return buffer.toString()
    }

    fun createTextMessage(content: String): NdefMessage? {
        try {
            val lang = Locale.getDefault().getLanguage().toByteArray()
            val text = content.toByteArray(charset("UTF-8")) // Content in UTF-8
            val payload = ByteArrayOutputStream(1 + lang.size + text.size)
            payload.write((lang.size and 0x1F))
            payload.write(lang, 0, lang.size)
            payload.write(text, 0, text.size)
            val record = NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT, ByteArray(0),
                payload.toByteArray()
            )
            return NdefMessage(arrayOf(record))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun writeTag(tag: Tag?, message: NdefMessage?) {
        if (tag != null) {
            try {
                val ndefTag = Ndef.get(tag)
                if (ndefTag == null) {
                    // Let's try to format the Tag in NDEF
                    val nForm = NdefFormatable.get(tag)
                    if (nForm != null) {
                        nForm.connect()
                        nForm.format(message)
                        nForm.close()
                    }
                } else {
                    ndefTag.connect()
                    ndefTag.writeNdefMessage(message)
                    ndefTag.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }
}

@Entity
data class Command(
    @PrimaryKey val uid: Int,
    @ColumnInfo(name = "nfc_id") val nfcId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: String
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

package com.dmitriy.android.serialportturkish

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.scale_activity.*

import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.util.*
import java.util.zip.CRC32
import kotlin.concurrent.fixedRateTimer
import kotlin.text.StringBuilder

class ScaleActivity : AppCompatActivity(), View.OnClickListener, TextWatcher {


    val lastCorrectData = mutableListOf<Byte>()
    val answerZZ = mutableListOf<Byte>()
    var timer : Timer? = null
    var isCommunicateNow = false

    /*
     * Notifications from UsbService will be received here.
     */
    private val mUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbService.ACTION_USB_PERMISSION_GRANTED     -> Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show()                  // USB PERMISSION GRANTED
                UsbService.ACTION_USB_PERMISSION_NOT_GRANTED -> Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show() // USB PERMISSION NOT GRANTED
                UsbService.ACTION_NO_USB                     -> Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show()           // NO USB CONNECTED
                UsbService.ACTION_USB_DISCONNECTED           -> Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show()           // USB DISCONNECTED
                UsbService.ACTION_USB_NOT_SUPPORTED          -> Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show()   // USB NOT SUPPORTED
            }
        }
    }
    private var usbService: UsbService? = null
    private var mHandler: MyHandler? = null

    private val usbConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, arg1: IBinder) {
            usbService = (arg1 as UsbService.UsbBinder).service
            usbService!!.setHandler(mHandler as Handler)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            usbService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scale_activity)
        mHandler = MyHandler(this)
        buttonSend.setOnClickListener(this)
        buttonStop.setOnClickListener(this)
        editText.addTextChangedListener(this)
    }

    public override fun onResume() {
        super.onResume()
        setFilters()  // Start listening notifications from UsbService
        startService(UsbService::class.java, usbConnection, null) // Start UsbService(if it was not started before) and Bind it
    }

    public override fun onPause() {
        timer?.cancel()
        super.onPause()
        unregisterReceiver(mUsbReceiver)
        unbindService(usbConnection)
    }

    private fun startService(service: Class<*>, serviceConnection: ServiceConnection, extras: Bundle?) {
        if (!UsbService.SERVICE_CONNECTED) {
            val startService = Intent(this, service)
            if (extras != null && !extras.isEmpty) {
                val keys = extras.keySet()
                for (key in keys) {
                    val extra = extras.getString(key)
                    startService.putExtra(key, extra)
                }
            }
            startService(startService)
        }
        val bindingIntent = Intent(this, service)
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED)
        filter.addAction(UsbService.ACTION_NO_USB)
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED)
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED)
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)
        registerReceiver(mUsbReceiver, filter)
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.buttonSend -> {
                buttonSend.isEnabled = false
                startRequest()
            }
            R.id.buttonStop -> {
                timer?.cancel()
                buttonSend.isEnabled = true
            }
        }

    }

    private fun startRequest(){
        if (editText!!.text.toString() != "") {
            val data = editText!!.text.toString()
            if (usbService != null) { // if UsbService was correctly binded, Send data
                Log.i("@@@", "Frame string " + " bytearrray: " + Frame.transmit(data.toInt(), null, null).byteArray.dumpHexString())
                isCommunicateNow = true
                usbService!!.write(Frame.transmit(data.toInt(), null, null).byteArray)
            }
        }
    }

    fun checkSumRequest() {
        val polynomial = answerZZ.subList(5, 6).toByteArray()
        val checksum = calculateChecksum(polynomial)
        val checksumFrame = Frame.checksum(checksum)
        usbService!!.write(checksumFrame.byteArray)
    }

    private fun requestConfirm() {
        usbService!!.write(Frame.confirm().byteArray)
    }

    private fun errorStatusRequest() {
        usbService!!.write(Frame.requestForStatusInformation().byteArray)
    }

    fun calculateChecksum(polynomial: ByteArray): Long = CRC32().apply { update(polynomial) }.value


    fun saveAnswerZZ(data: List<Byte>) {
        answerZZ.addAll(data)
    }

    fun getSavedData() = lastCorrectData

    fun saveLastCorrectValue(data: List<Byte>){
        lastCorrectData.clear()
        lastCorrectData.addAll(data)
    }

    fun showError(error : String){
        if(display.text.isNotEmpty()) clearDisplay()
        when(error){
            "3031"-> display.text = resources.getString(R.string.error1)
            "3032"-> display.text = resources.getString(R.string.error2)
            "3130"-> display.text = resources.getString(R.string.error3)
            "3131"-> display.text = resources.getString(R.string.error4)
            "3132"-> display.text = resources.getString(R.string.error5)
            "3133"-> display.text = resources.getString(R.string.error6)
            "3230"-> display.text = resources.getString(R.string.error7)
            "3231"-> display.text = String(getSavedData().toByteArray(),Charset.forName("UTF-8"))
            "3232"-> display.text = resources.getString(R.string.error8)
            "3330"-> display.text = resources.getString(R.string.error9)
            "3331"-> display.text = resources.getString(R.string.error10)
            "3332"-> display.text = resources.getString(R.string.error11)
        }
        isCommunicateNow = false
        startTimer()
    }

    fun clearDisplay() {
        display.text = ""
    }



    fun showAnswerFromScale(answer : String){
        buttonSend.isEnabled = true
        display.text = answer
        tvWeight.text = parceWeight(answer.substring(6,11))
        tvPrice.text = parcePrice(answer.substring(12,18))
        tvCalculate.text = parceCalculation(answer.substring(19,25))
        startTimer()
    }

    fun startTimer(){
        if(timer == null){
            timer = fixedRateTimer(initialDelay = 500, period = 500) {
                if (!isCommunicateNow) {
                    mHandler?.clearData()
                    startRequest()
                }
            }
        }
    }

    //region EditText text change listener
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        timer?.cancel()
        timer = null
    }
    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }
    //endregion

    //region ParsingAnswer
    fun parceWeight(value : String):String{
        if(value.contains("."))value.replace(".","")
        Log.i("@@@","weight:" + value + "End")
        val positionOfpoint = value.length - 3
        return StringBuilder(value.substring(0,positionOfpoint)).append(",").append(value.substring(positionOfpoint,value.length)).toString()
    }

    fun parcePrice(value : String):String{
        if(value.contains("."))value.replace(".","")
        Log.i("@@@","price:" + value + "End")
        val positionOfpoint = value.length - 2
        return StringBuilder(value.substring(0,positionOfpoint)).append(",").append(value.substring(positionOfpoint,value.length)).toString()
    }

    fun parceCalculation(value : String):String{
        if(value.contains("."))value.replace(".","")
        Log.i("@@@","calculation:" + value + "End")
        val positionOfpoint = value.length - 2
        return StringBuilder(value.substring(0,positionOfpoint)).append(",").append(value.substring(positionOfpoint,value.length)).toString()
    }
    //endregion

    //region Class MyHandler
    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private class MyHandler(activity: ScaleActivity) : Handler() {
        private val mActivity = WeakReference(activity)

        private val stringBuilder = StringBuilder()
        private val stringBuilderDisplay = StringBuilder()

        var receivedData = mutableListOf<Byte>()

        fun clearData(){
            receivedData.clear()
            stringBuilder.clear()
        }


        override fun handleMessage(msg: Message) {
            when(msg.what){
                UsbService.MESSAGE_FROM_SERIAL_PORT -> {
                    val data =  msg.obj as ByteArray
                    stringBuilder.append(data.toHexString())
                    receivedData.addAll(data.toList())
                    Log.i("@@@", "Received data in handler start: " + stringBuilder.toString())

                    if (data.any { it == 0x03.toByte() } && stringBuilder.toString().length == 16) {// получение первого ответа
                        Log.i("@@@", "Save data ZZ  ${data.toHexString()}")
                        mActivity.get()?.saveAnswerZZ(receivedData)//сохраняем данные для просчета чексуммы
                        clearData()
                        mActivity.get()?.checkSumRequest()// отправка чексуммы
                    }
                    if (data.any { it == 0x06.toByte() }) {// тут все хорошо Ak пришло
                        Log.i("@@@", "Received data in handler in moment 06: " + data.toHexString() + "string builder : $stringBuilder")
                        clearData()
                        mActivity.get()?.requestConfirm()
                    }

                    if (stringBuilder.toString() == "0231311B3103") {// пришло Sx11Ec1Ex
                        clearData()
                        mActivity.get()?.requestConfirm()
                    }

                    if(data.any{ it == 0x015.toByte() }) {//пришла ошибка
                        clearData()
                        mActivity.get()?.errorStatusRequest() //делаем запрос на проверку  значения ошибки
                    }
                    if(stringBuilder.length > 13 && stringBuilder.toString().substring(0,8) == "0230391B"){
                        mActivity.get()?.showError(stringBuilder.toString().substring(8,12))// показываем ошибку
                        stringBuilderDisplay.clear() }

                    var displayData = String(data, Charset.forName("UTF-8"))//пришел конечный ответ
                    stringBuilderDisplay.append(displayData)
                    if(stringBuilder.toString().length == 52){
                        val finishValue = stringBuilderDisplay.toString()
                        mActivity.get()?.clearDisplay()
                        mActivity.get()?.saveLastCorrectValue(receivedData)

                        Log.d("@@@@", "DisplayData :$finishValue" + "END")
                        stringBuilderDisplay.clear()
                        receivedData.clear()
                        stringBuilder.clear()
                        mActivity.get()?.isCommunicateNow = false

                        mActivity.get()?.showAnswerFromScale(String(mActivity.get()?.getSavedData()!!.toByteArray(),Charset.forName("UTF-8")))

                    }
                }
                UsbService.CTS_CHANGE -> Toast.makeText(mActivity.get(), "CTS_CHANGE", Toast.LENGTH_LONG).show()
                UsbService.DSR_CHANGE -> Toast.makeText(mActivity.get(), "DSR_CHANGE", Toast.LENGTH_LONG).show()
            }
        }
    }
    //endregion
}
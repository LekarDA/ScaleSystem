package com.dmitriy.android.serialportturkish.old_version

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import com.dmitriy.android.serialportturkish.R
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class MainActivity : CoroutineAppCompatActivity() {

//    lateinit var device : UsbDevice
//    lateinit var usbConnection: UsbDeviceConnection
//
    val callback = UsbSerialInterface.UsbReadCallback {
        data -> Log.e("SerialTurkish","data: "+ data.toString())
    }

    lateinit var usbManager: UsbManager
    var device: UsbDevice? = null
    var serialDevice: UsbSerialDevice? = null
    var connection: UsbDeviceConnection? = null

    val ACTION_USB_PERMISSION = "permission"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter =  IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(broadcastReceiver,filter)

        on.setOnClickListener { sendData("o") }
        off.setOnClickListener { sendData("x") }
        disconnect.setOnClickListener { disconnect() }
        connect.setOnClickListener { startUsbConnecting() }
        read.setOnClickListener {
            launch {read()}
            }


//
//        val supported = UsbSerialDevice.isSupported(device)
//        val serialDevice = UsbSerialDevice.createUsbSerialDevice(UsbSerialDevice.FTDI,device,usbConnection,0)
//        var isOpened = serialDevice.open()
//        serialDevice.setBaudRate(9600)
//        serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8)
//        serialDevice.setParity(UsbSerialInterface.PARITY_ODD)
//        serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_RTS_CTS)
//        launch { serialDevice.read(callback) }
    }

    private fun startUsbConnecting(){
        val usbDevices:HashMap<String,UsbDevice>? = usbManager.deviceList
        if(usbDevices?.isEmpty()!!){
            var keep = true
            usbDevices.forEach{ entry ->
                device = entry.value
                val vendorId: Int? = device?.vendorId
                Log.i("SerialTurkish","vendorId: "+ vendorId)
                if(vendorId == 6790){
                    val intent :PendingIntent = PendingIntent.getBroadcast(this,0,Intent(ACTION_USB_PERMISSION),0)
                    usbManager.requestPermission(device,intent)
                    keep = false
                    Log.i("SerialTurkish","connection successful")
                } else{
                    connection = null
                    device = null
                    Log.i("SerialTurkish","unable to connect")
                }
                if(!keep){
                    return
                }
            }
        }else{
            Log.i("SerialTurkish","no usb device connected")
        }
    }

    private fun sendData(input:String){
        serialDevice?.write(input.toByteArray())
        Log.i("SerialTurkish","sending Data" + input.toByteArray())
    }

    private suspend fun read(){
        while (isActive) {
            launch { serialDevice?.read(callback) }
        }
    }

    private fun disconnect(){
        serialDevice?.close()
    }

    private val broadcastReceiver = object:BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action!! == ACTION_USB_PERMISSION){
                val granted:Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if(granted){
                    connection = usbManager.openDevice(device)
                    serialDevice = UsbSerialDevice.createUsbSerialDevice(device,connection)
                    if(serialDevice!=null){
                        if(serialDevice!!.open()){
                            serialDevice!!.setBaudRate(9600)
                            serialDevice!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                            serialDevice!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                            serialDevice!!.setParity(UsbSerialInterface.PARITY_NONE)
                            serialDevice!!.setFlowControl(UsbSerialInterface./*FLOW_CONTROL_RTS_CTS*/FLOW_CONTROL_OFF)
                        }else{
                            Log.i("SerialTurkish","port not open")
                        }
                    }else{
                        Log.i("SerialTurkish","port is null")
                    }
                } else {
                    Log.i("SerialTurkish","permission not granted")
                }
            } else if(intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED){
                startUsbConnecting()
            } else if(intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED){
                disconnect()
            }

        }
    }


}

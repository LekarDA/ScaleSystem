package com.dmitriy.android.serialportturkish

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log

import com.felhr.usbserial.CDCSerialDevice
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface

import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.HashMap

class UsbService : Service() {

    private val binder = UsbBinder()

    private var context: Context? = null
    private var mHandler: Handler? = null
    private var usbManager: UsbManager? = null
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var serialPort: UsbSerialDevice? = null

    private var serialPortConnected: Boolean = false

    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */
    private val mCallback = UsbSerialInterface.UsbReadCallback { receivedBytes ->
        val result = StringBuilder()
        try {
            var data = String(receivedBytes, Charset.forName("UTF-8"))
            Log.i("@@@", "callback read receivedBytes: $receivedBytes")
            Log.i("@@@", "callback read data: $data")
            Log.i("@@@", "Hex receivedBytes" + receivedBytes.dumpHexString() + "here end")
            val array = receivedBytes.dumpHexString().split(" ")
            if(array[1] =="03")
                data = END_OF_DATA
            else {
               Log.i("@@@","HEX_STRING: " + result.append("0x").append(array[1]).toString())
                data = result.append("|").toString()
            }
            Log.i("@@@", "Hex" + receivedBytes.dumpHexString())
            if (mHandler != null)
                mHandler!!.obtainMessage(MESSAGE_FROM_SERIAL_PORT, /*data*/receivedBytes).sendToTarget()
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
    }

    /*
     * State changes in the CTS line will be received here
     */
    private val ctsCallback = UsbSerialInterface.UsbCTSCallback {
        if (mHandler != null)
            mHandler!!.obtainMessage(CTS_CHANGE).sendToTarget()
    }

    /*
     * State changes in the DSR line will be received here
     */
    private val dsrCallback = UsbSerialInterface.UsbDSRCallback {
        if (mHandler != null)
            mHandler!!.obtainMessage(DSR_CHANGE).sendToTarget()
    }
    /*
     * Different notifications from OS will be received here (USB attached, detached, permission responses...)
     * About BroadcastReceiver: http://developer.android.com/reference/android/content/BroadcastReceiver.html
     */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, arg1: Intent) {
            if (arg1.action == ACTION_USB_PERMISSION) {
                val granted = arg1.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted)
                // User accepted our USB connection. Try to open the device as a serial port
                {
                    val intent = Intent(ACTION_USB_PERMISSION_GRANTED)
                    arg0.sendBroadcast(intent)
                    connection = usbManager!!.openDevice(device)
                    ConnectionThread().start()
                } else
                // User not accepted our USB connection. Send an Intent to the Main Activity
                {
                    val intent = Intent(ACTION_USB_PERMISSION_NOT_GRANTED)
                    arg0.sendBroadcast(intent)
                }
            } else if (arg1.action == ACTION_USB_ATTACHED) {
                if (!serialPortConnected)
                    findSerialPortDevice() // A USB device has been attached. Try to open it as a Serial port
            } else if (arg1.action == ACTION_USB_DETACHED) {
                // Usb device was disconnected. send an intent to the Main Activity
                val intent = Intent(ACTION_USB_DISCONNECTED)
                arg0.sendBroadcast(intent)
                if (serialPortConnected) {
                    serialPort!!.close()
                }
                serialPortConnected = false
            }
        }
    }

    /*
     * onCreate will be executed when service is started. It configures an IntentFilter to listen for
     * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
     */
    override fun onCreate() {
        this.context = this
        serialPortConnected = false
        UsbService.SERVICE_CONNECTED = true
        setFilter()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        findSerialPortDevice()
    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPort!!.close()
        unregisterReceiver(usbReceiver)
        UsbService.SERVICE_CONNECTED = false
    }

    /*
     * This function will be called from MainActivity to write data through Serial Port
     */
    fun write(data: ByteArray) {
        if (serialPort != null)
            serialPort!!.write(data)
    }

    fun setHandler(mHandler: Handler) {
        this.mHandler = mHandler
    }

    private fun findSerialPortDevice() {
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        val usbDevices = usbManager!!.deviceList
        if (!usbDevices.isEmpty()) {

            // first, dump the hashmap for diagnostic purposes
            for ((_, value) in usbDevices) {
                device = value
                Log.d(
                    TAG, String.format(
                        "USBDevice.HashMap (vid:pid) (%X:%X)-%b class:%X:%X name:%s",
                        device!!.vendorId, device!!.productId,
                        UsbSerialDevice.isSupported(device!!),
                        device!!.deviceClass, device!!.deviceSubclass,
                        device!!.deviceName
                    )
                )
            }

            for ((_, value) in usbDevices) {
                device = value
                val deviceVID = device!!.vendorId
                val devicePID = device!!.productId

                //                if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003) && deviceVID != 0x5c6 && devicePID != 0x904c) {
                if (UsbSerialDevice.isSupported(device!!)) {
                    // There is a supported device connected - request permission to access it.
                    requestUserPermission()
                    break
                } else {
                    connection = null
                    device = null
                }
            }
            if (device == null) {
                // There are no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                val intent = Intent(ACTION_NO_USB)
                sendBroadcast(intent)
            }
        } else {
            Log.d(TAG, "findSerialPortDevice() usbManager returned empty device list.")
            // There is no USB devices connected. Send an intent to MainActivity
            val intent = Intent(ACTION_NO_USB)
            sendBroadcast(intent)
        }
    }

    private fun setFilter() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(ACTION_USB_DETACHED)
        filter.addAction(ACTION_USB_ATTACHED)
        registerReceiver(usbReceiver, filter)
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private fun requestUserPermission() {
        Log.d(TAG, String.format("requestUserPermission(%X:%X)", device!!.vendorId, device!!.productId))
        val mPendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        usbManager!!.requestPermission(device, mPendingIntent)
    }

    inner class UsbBinder : Binder() {
        val service: UsbService
            get() = this@UsbService
    }

    /*
     * A simple thread to open a serial port.
     * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
     */
    private inner class ConnectionThread : Thread() {
        override fun run() {
            serialPort = UsbSerialDevice.createUsbSerialDevice(device!!, connection)
            if (serialPort != null) {
                if (serialPort!!.open()) {
                    serialPortConnected = true
                    serialPort!!.setBaudRate(BAUD_RATE)
                    serialPort!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                    serialPort!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                    serialPort!!.setParity(UsbSerialInterface.PARITY_NONE)
                    /**
                     * Current flow control Options:
                     * UsbSerialInterface.FLOW_CONTROL_OFF
                     * UsbSerialInterface.FLOW_CONTROL_RTS_CTS only for CP2102 and FT232
                     * UsbSerialInterface.FLOW_CONTROL_DSR_DTR only for CP2102 and FT232
                     */
                    serialPort!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_RTS_CTS)
                    serialPort!!.read(mCallback)
                    serialPort!!.getCTS(ctsCallback)
                    serialPort!!.getDSR(dsrCallback)

                    //
                    // Some Arduinos would need some sleep because firmware wait some time to know whether a new sketch is going
                    // to be uploaded or not
                    //Thread.sleep(2000); // sleep some. YMMV with different chips.

                    // Everything went as expected. Send an intent to MainActivity
                    val intent = Intent(ACTION_USB_READY)
                    context!!.sendBroadcast(intent)
                } else {
                    // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                    // Send an Intent to Main Activity
                    if (serialPort is CDCSerialDevice) {
                        val intent = Intent(ACTION_CDC_DRIVER_NOT_WORKING)
                        context!!.sendBroadcast(intent)
                    } else {
                        val intent = Intent(ACTION_USB_DEVICE_NOT_WORKING)
                        context!!.sendBroadcast(intent)
                    }
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                val intent = Intent(ACTION_USB_NOT_SUPPORTED)
                context!!.sendBroadcast(intent)
            }
        }
    }

    companion object {

        val TAG = "UsbService"
        val END_OF_DATA = "END_OF_DATA"
        val ACTION_USB_READY = "com.felhr.connectivityservices.USB_READY"
        val ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
        val ACTION_USB_NOT_SUPPORTED = "com.felhr.usbservice.USB_NOT_SUPPORTED"
        val ACTION_NO_USB = "com.felhr.usbservice.NO_USB"
        val ACTION_USB_PERMISSION_GRANTED = "com.felhr.usbservice.USB_PERMISSION_GRANTED"
        val ACTION_USB_PERMISSION_NOT_GRANTED = "com.felhr.usbservice.USB_PERMISSION_NOT_GRANTED"
        val ACTION_USB_DISCONNECTED = "com.felhr.usbservice.USB_DISCONNECTED"
        val ACTION_CDC_DRIVER_NOT_WORKING = "com.felhr.connectivityservices.ACTION_CDC_DRIVER_NOT_WORKING"
        val ACTION_USB_DEVICE_NOT_WORKING = "com.felhr.connectivityservices.ACTION_USB_DEVICE_NOT_WORKING"
        val MESSAGE_FROM_SERIAL_PORT = 0
        val CTS_CHANGE = 1
        val DSR_CHANGE = 2
        private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        private val BAUD_RATE = 9600 // BaudRate. Change this value if you need
        var SERVICE_CONNECTED = false
    }
}

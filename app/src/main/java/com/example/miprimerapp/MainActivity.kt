package com.example.miprimerapp

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.miprimerapp.ui.theme.MiprimerAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiprimerAppTheme {
                MyApp()
            }
        }
    }
}

@Composable
fun MyApp() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "screen1") {
        composable("screen1") { Screen1(navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen1(navController: androidx.navigation.NavHostController) {
    var connectionStatus by remember { mutableStateOf("Not Connected") }
    var receivedImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val localIp by remember { mutableStateOf(getLocalIpAddress()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text("Pantalla 1", onTextLayout = {}) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF6200EE),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bienvenido a la Pantalla 1",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                onTextLayout = {}
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = connectionStatus,
                onTextLayout = {}
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                discoverAndReceiveImage(localIp) { status, image ->
                    connectionStatus = status
                    receivedImage = image
                }
            }) {
                Text(
                    text = "Recibir Captura de Pantalla",
                    onTextLayout = {}
                )

            }
            Spacer(modifier = Modifier.height(16.dp))
            receivedImage?.let {
                Image(bitmap = it, contentDescription = "Received Image", modifier = Modifier.size(200.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("screen2") }) {
                Text(
                    text = "Ir a Pantalla 2",
                    onTextLayout = {}
                )

            }
        }
    }
}

private fun discoverAndReceiveImage(localIp: String?, updateStatusAndImage: (String, androidx.compose.ui.graphics.ImageBitmap?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val discoveryPort = 8888
            val socket = DatagramSocket()
            socket.broadcast = true

            val sendData = "SCREENSHOT_REQUEST".toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, discoveryPort)
            socket.send(sendPacket)

            val receiveData = ByteArray(65507) // Tamaño máximo de un paquete UDP
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.soTimeout = 5000 // 5 segundos de tiempo de espera

            try {
                socket.receive(receivePacket)
                val serverIp = receivePacket.address.hostAddress
                Log.d("NetworkNotification", "Captura de pantalla recibida del servidor en $serverIp")

                if (serverIp != localIp) {
                    val receivedImageByteArray = receivePacket.data.copyOfRange(0, receivePacket.length)
                    val receivedImageBitmap = BitmapFactory.decodeByteArray(receivedImageByteArray, 0, receivedImageByteArray.size)

                    if (receivedImageBitmap != null) {
                        withContext(Dispatchers.Main) {
                            updateStatusAndImage("Captura de pantalla recibida", receivedImageBitmap.asImageBitmap())
                        }
                    } else {
                        Log.e("BitmapFactory", "Decoding returned null bitmap")
                        withContext(Dispatchers.Main) {
                            updateStatusAndImage("Error al decodificar la imagen recibida", null)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateStatusAndImage("Descubierto el propio dispositivo, no se recibe la captura de pantalla", null)
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    updateStatusAndImage("No se encontró ningún servidor que enviara la captura de pantalla", null)
                }
            }

            socket.close()
        } catch (e: Exception) {
            Log.e("NetworkNotification", "Error en la detección y recepción de la captura de pantalla", e)
            withContext(Dispatchers.Main) {
                updateStatusAndImage("Error en la detección y recepción de la captura de pantalla: ${e.message}", null)
            }
        }
    }
}

private fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val inetAddresses = networkInterface.inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val inetAddress = inetAddresses.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (ex: SocketException) {
        Log.e("NetworkUtils", "Error getting local IP address", ex)
    }
    return null
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MiprimerAppTheme {
        MyApp()
    }
}

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

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
        composable("screen2") { Screen2(navController) }
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
                title = { Text("Pantalla 1") },
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
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = connectionStatus)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                discoverAndReceiveImage(localIp) { status, image ->
                    connectionStatus = status
                    receivedImage = image
                }
            }) {
                Text("Recibir Captura de Pantalla")
            }
            Spacer(modifier = Modifier.height(16.dp))
            receivedImage?.let {
                Image(bitmap = it, contentDescription = "Received Image", modifier = Modifier.size(200.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("screen2") }) {
                Text("Ir a Pantalla 2")
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
            } catch (e: SocketTimeoutException) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen2(navController: androidx.navigation.NavHostController) {
    var connectionStatus by remember { mutableStateOf("Not Connected") }
    var message by remember { mutableStateOf("") }
    var receivedMessage by remember { mutableStateOf("") }
    val localIp by remember { mutableStateOf(getLocalIpAddress()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pantalla 2") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF03DAC5),
                    titleContentColor = Color.Black
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
                text = "Bienvenido a la Pantalla 2",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = connectionStatus)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensaje") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                discoverAndSendMessage(localIp, message) { status ->
                    connectionStatus = status
                }
            }) {
                Text("Enviar Mensaje")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Mensaje Recibido: $receivedMessage")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("screen1") }) {
                Text("Ir a Pantalla 1")
            }
        }
    }

    LaunchedEffect(Unit) {
        startServer(localIp) { msg ->
            receivedMessage = msg
        }
    }
}

private fun discoverAndSendMessage(localIp: String?, message: String, updateStatus: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val discoveryPort = 8888
            val socket = DatagramSocket()
            socket.broadcast = true

            val sendData = "DISCOVER_REQUEST".toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, discoveryPort)
            socket.send(sendPacket)

            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.soTimeout = 5000 // 5 segundos de tiempo de espera

            try {
                socket.receive(receivePacket)
                val serverIp = receivePacket.address.hostAddress
                Log.d("NetworkNotification", "Servidor encontrado en $serverIp")

                if (serverIp != localIp) {
                    // Enviar mensaje al servidor descubierto
                    if (serverIp != null) {
                        connectAndSendMessage(serverIp, 8080, message, localIp, updateStatus)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateStatus("Descubierto el propio dispositivo, no se envía el mensaje.")
                    }
                }
            } catch (e: SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    updateStatus("No se encontró ningún servidor.")
                }
            }

            socket.close()
        } catch (e: Exception) {
            Log.e("NetworkNotification", "Error en la detección y envío del mensaje", e)
            withContext(Dispatchers.Main) {
                updateStatus("Error en la detección y envío del mensaje: ${e.message}")
            }
        }
    }
}

private fun connectAndSendMessage(ip: String, port: Int, message: String, localIp: String?, updateStatus: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        var socket: Socket? = null
        try {
            Log.d("NetworkNotification", "Intentando conectar a $ip:$port desde $localIp")
            socket = Socket(ip, port)
            val output: OutputStream = socket.getOutputStream()
            val fullMessage = "From $localIp: $message"
            output.write(fullMessage.toByteArray())
            output.flush()
            withContext(Dispatchers.Main) {
                updateStatus("Mensaje enviado correctamente a $ip")
                Log.d("NetworkNotification", "Mensaje enviado correctamente a $ip")
            }
        } catch (e: Exception) {
            Log.e("NetworkNotification", "Error al enviar el mensaje a $ip", e)
            withContext(Dispatchers.Main) {
                updateStatus("Error al enviar el mensaje a $ip: ${e.message}")
            }
        } finally {
            socket?.close()
        }
    }
}

private fun startServer(localIp: String?, onMessageReceived: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        var serverSocket: ServerSocket? = null
        try {
            Log.d("NetworkNotification", "Servidor iniciado, esperando conexiones...")
            serverSocket = ServerSocket(8080)

            // Hilo para escuchar descubrimientos de clientes
            val discoveryPort = 8888
            CoroutineScope(Dispatchers.IO).launch {
                var discoverySocket: DatagramSocket? = null
                try {
                    discoverySocket = DatagramSocket(discoveryPort)
                    while (true) {
                        val receiveData = ByteArray(1024)
                        val receivePacket = DatagramPacket(receiveData, receiveData.size)
                        discoverySocket.receive(receivePacket)

                        if (receivePacket.address.hostAddress != localIp) {
                            val sendData = "DISCOVER_RESPONSE".toByteArray()
                            val sendPacket = DatagramPacket(sendData, sendData.size, receivePacket.address, receivePacket.port)
                            discoverySocket.send(sendPacket)

                            Log.d("NetworkNotification", "Respuesta de descubrimiento enviada a ${receivePacket.address.hostAddress}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NetworkNotification", "Error en la respuesta de descubrimiento", e)
                } finally {
                    discoverySocket?.close()
                }
            }

            while (true) {
                var clientSocket: Socket? = null
                try {
                    clientSocket = serverSocket.accept()
                    Log.d("NetworkNotification", "Conexión aceptada de ${clientSocket.inetAddress.hostAddress}")
                    val input: InputStream = clientSocket.getInputStream()
                    val message = input.bufferedReader().readLine()
                    withContext(Dispatchers.Main) {
                        onMessageReceived(message)
                        Log.d("NetworkNotification", "Mensaje recibido: $message")
                    }
                } catch (e: Exception) {
                    Log.e("NetworkNotification", "Error en el servidor", e)
                } finally {
                    clientSocket?.close()
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkNotification", "Error en el servidor", e)
        } finally {
            serverSocket?.close()
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

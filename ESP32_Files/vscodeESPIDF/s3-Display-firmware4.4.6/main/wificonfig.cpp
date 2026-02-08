#include "wificonfig.h"

const char* DEVICE_NAME = "ESP_DISPLAY";
const char* targetIP = "192.168.10.2"; 
const int udpPort = 5005;
WiFiUDP udp;
unsigned long lastSend = 0;
char wifiReceiveBuffer[128];
String wifissid;
String wifipassword;

void wifi_send(const char* message) {
  if (millis() - lastSend > 1000) {
    lastSend = millis();

    udp.beginPacket(targetIP, udpPort);
    udp.print(message);
    udp.endPacket();

    Serial.println("Sent message");
  }
}

void wifi_receive(void) {
  static char buf[128];   // static avoids stack churn (nice on ESP32)

  int packetSize = udp.parsePacket();
  if (packetSize <= 0) return;

  int len = udp.read(buf, sizeof(buf) - 1);
  if (len <= 0) return;

  buf[len] = '\0';

  Serial.print("Received: ");
  Serial.println(buf);

  // Robust command match (ignores trailing junk)
  if (strncmp(buf, "INTRUDER INTRUDER", 16) == 0) {

    udp.beginPacket("192.168.1.74", 5005);
    udp.print("INTRUDER INTRUDER\n");
    udp.endPacket();
  }
}

void wifiapstart(){
  IPAddress apIP(192,168,10,1);
  IPAddress gateway(192,168,10,1);
  IPAddress subnet(255,255,255,0);
  WiFi.softAPConfig(apIP, gateway, subnet);
  WiFi.softAP("ESP32_Master_Config", "12345678");
}

String memoryssid = "";
String memorypass = "";
void getcred(){
  memoryssid = littlefsReadFile("/wifissid.txt");
  memorypass = littlefsReadFile("/wifipass.txt");
}

void wifistastart(){
  getcred();
  WiFi.begin(memoryssid, memorypass);
}

static bool serverStarted = false;
void wifiInit(){
  WiFi.mode(WIFI_AP_STA);
  wifiapstart();
  delay(1000);
  wifistastart();
  udp.begin(udpPort);
}

void setuppageserver(){
  if (!homepage && setuppage && !disarmauthpage){
        
    if (!serverStarted) {
        server.begin();
        serverStarted = true;
        Serial.print("server started");
    }
    server.handleClient();
  }
  else{
      if (serverStarted) {
        server.stop();
        serverStarted = false;
        Serial.print("server stopped");
      }
  }
}
#include "driver/gptimer.h"
#include "driver/gpio.h"
#include <stdbool.h>
//////////////////////////////////////////////////////////////////////
#include <WiFi.h>
#include <WiFiUdp.h>
//////////////////////////////////////////////////////////////////////

#define ECHO 16
#define TRIG 17

//////////////////////////////////wifisetup/////////////////////////////////////
// WiFi credentials 
const char* ssid = "vvlowspeed";
const char* password = "10022024";

// Receiver ESP32 IP address 
IPAddress receiverIP(192, 168, 1, 71);  // Replace with your receiver ESP32's IP

// UDP settings
WiFiUDP udp;
const int udpPort = 12345;

// Connection status
bool wifiConnected = false;

/*
 * Modular function to transmit a float value and a string
 * Parameters:
 *   transmit_value: Float value to transmit
 *   transmit_string: String to transmit (max 200 characters)
 * Returns:
 *   true if transmission successful, false otherwise
 */
bool transmit(float transmit_value, const char* transmit_string) {
  // Check if WiFi is connected
  if (!wifiConnected || WiFi.status() != WL_CONNECTED) {
    Serial.println("Error: WiFi not connected. Cannot transmit.");
    return false;
  }
  
  // Validate string length (prevent buffer overflow)
  if (strlen(transmit_string) > 200) {
    Serial.println("Error: String too long (max 200 characters)");
    return false;
  }
  
  // Create packet: format is "VALUE:STRING"
  // Example: "11.593:Counter update"
  // IMPORTANT: Use %f or %.2f for float (NOT %d!)
  char packet[250];
  snprintf(packet, sizeof(packet), "%.2f:%s", transmit_value, transmit_string);
  
  // Send UDP packet
  udp.beginPacket(receiverIP, udpPort);
  udp.write((uint8_t*)packet, strlen(packet));
  
  if (udp.endPacket()) {
    Serial.print("Transmitted: Value=");
    Serial.print(transmit_value);
    Serial.print(", String=\"");
    Serial.print(transmit_string);
    Serial.println("\"");
    return true;
  } else {
    Serial.println("Error: Failed to send packet");
    return false;
  }
}
//////////////////////////////////wifisetup/////////////////////////////////////

uint64_t value = 0;
volatile unsigned int echo_time = 0;
volatile bool measurementFlag = false;
float distance = 0;

float lastDistance = 0;
float nowDistance = 0;


/////////////////////////////// TIMER INIT. /////////////////////////////////////////////
gptimer_handle_t gptimer = nullptr;

void init_timer(){
  gptimer_config_t config = {
      .clk_src = GPTIMER_CLK_SRC_DEFAULT,
      .direction = GPTIMER_COUNT_UP,
      .resolution_hz = 1000000,
  }; 

  ESP_ERROR_CHECK(gptimer_new_timer(&config, &gptimer));
  ESP_ERROR_CHECK(gptimer_enable(gptimer));
}
/////////////////////////////////////////////////////////////////////////////////////////
void timer_start(){
  gptimer_set_raw_count(gptimer, 0);
  gptimer_start(gptimer);
}
void timer_stop(){
  gptimer_get_raw_count(gptimer, &value);
  echo_time = (unsigned int)value;
  gptimer_stop(gptimer);
}

////////////////////////////////  INTERRUPT. ////////////////////////////////////////////
void IRAM_ATTR echo()
{
  if (digitalRead(ECHO) == HIGH){
    timer_start();
    measurementFlag = false;
  }
  if (digitalRead(ECHO) == LOW){
    timer_stop();
    measurementFlag = true;
  }
}

void setup() {
  Serial.begin(115200);
  init_timer();
  //////////////////////////////// SETTING ECHO GPIO ////////////////////////////////////////////
  pinMode(ECHO, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(ECHO),
                  echo,
                  CHANGE); //DETECT BOTH EDGES
  ///////////////////////////////////////////////////////////////////////////////////////////////
  pinMode(TRIG, OUTPUT);
  //////////////////////////////////wifisetup////////////////////////////////////////////////////
  Serial.println("ESP32 Wireless Transmitter");
  Serial.println("==========================");
  
  // Connect to WiFi
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    Serial.println();
    Serial.println("WiFi connected!");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
    Serial.print("Receiver IP: ");
    Serial.println(receiverIP);
    Serial.print("UDP Port: ");
    Serial.println(udpPort);
  } else {
    Serial.println();
    Serial.println("WiFi connection failed!");
    Serial.println("Please check your SSID and password.");
  }
  
  // Initialize UDP
  udp.begin(udpPort);
  //////////////////////////////////wifisetup////////////////////////////////////////////////////
}

void loop() {
  //measurementFlag = false;
  digitalWrite(TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG, LOW);
  while (measurementFlag == false){

  }
  distance = echo_time * 0.0343 / 2;
  
  lastDistance = nowDistance;
  nowDistance = distance;

  if(lastDistance - nowDistance > 10){
    int count = 0;
    while(count < 10){
      Serial.printf("FUCK U FUCK U FUCK U\n");
      count++;
    }
  }
 
  Serial.printf("Distance: %.3f   Last: %.3f   Now: %.3f\n", distance, lastDistance, nowDistance);
  delay(200);
  /////////////////////////////wifisetup////////////////////////////////////////////
  // WiFi transmission - with rate limiting
  static unsigned long lastTransmit = 0;
  
  // Transmit every 200ms minimum (was 100ms - too fast!)
  // WiFi UDP needs time between packets to avoid buffer overflow
  if (millis() - lastTransmit > 200) {
    // Check if previous packet finished sending
    // Don't start new transmission if WiFi is busy
    if (WiFi.status() == WL_CONNECTED) {
      transmit(distance, "Distance update");
      lastTransmit = millis();
    }
  }
  /////////////////////////////wifisetup////////////////////////////////////////////
}

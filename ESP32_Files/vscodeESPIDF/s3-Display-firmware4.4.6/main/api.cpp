#include "wificonfig.h"

WebServer server(80);

void apihealth(){
    server.send(200, "application/json", "{\"esp32\" : \"ok\"}");
}

void apicreds(){
    String json = "{\n  \"SSID\": \"" + wifissid + "\",\n  \"PASS\": \"" + wifipassword + "\"\n}";
    server.send(200, "application/json", json);
}

void apinewssid(){
    String newssid = server.arg("SSID");
    Serial.println("SSID:" + newssid);
    server.send(200, "text/plain", "OK");
}
void apinewpass(){
    String newpass = server.arg("pass");
    Serial.println("Password:" + newpass);
    server.send(200, "text/plain", "OK");
}


void apihandle(){
    server.on("/api/health", HTTP_GET, apihealth);
    server.on("/api/creds", HTTP_GET, apicreds);
    server.on("/api/newpass", HTTP_POST, apinewpass);
    server.on("/api/newssid", HTTP_POST, apinewssid);
}
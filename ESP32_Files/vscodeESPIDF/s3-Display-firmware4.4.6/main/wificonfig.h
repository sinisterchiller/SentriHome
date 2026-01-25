#ifndef WIFICONFIG_H
#define WIFICONFIG_H

#include "WiFi.h"
#include "WebServer.h"
#include "filesys.h"
#include "display.h"
#include <WiFiUdp.h>
#include "esp_wifi.h"


extern WebServer server;
void wifiInit(void);
void wifiupdate(void);
extern WiFiUDP udp;
void setuppageweb(void);
void wifi_send(const char* message);
void wifi_receive(void);


#endif 
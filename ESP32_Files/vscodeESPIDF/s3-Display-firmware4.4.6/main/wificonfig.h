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
extern WiFiUDP udp;
void setuppageserver(void);
void wifi_send(const char* message);
void wifi_receive(void);

void apihandle(void);
extern String wifissid;
extern String wifipassword;
extern int idscount;
extern String IDS[20];


#endif 
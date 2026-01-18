#ifndef WIFICONFIG_H
#define WIFICONFIG_H

#include "WiFi.h"
#include "WebServer.h"
#include "filesys.h"
#include "display.h"
#include <WiFiUdp.h>


extern WebServer server;
void wifiInit(void);
void wifiupdate(void);
void setuppageweb(void);
void wifi_send(const char* message);
extern int wifi_receive(void);


#endif 
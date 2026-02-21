#include "display.h"
#include "filesys.h"
#include "wificonfig.h"

void setup() {
  Serial.begin(115200);
  littlefsinit();
  wifiInit();
  displayinit();
  apihandle();
  server.begin();
}

void loop() {
  display();
  wifi_receive();
  //setuppageserver();
  server.handleClient();
}

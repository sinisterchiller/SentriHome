#include "tft_interface.h"
#include "display.h"
#include "filesys.h"
#include "wificonfig.h"

void setup() {
  Serial.begin(115200);
  littlefsinit();
  wifiInit();
  displayinit();
  apihandle();
}

void loop() {
  display();
  wifiupdate();
  wifi_receive();
  setuppageweb();
}

#include "tft_interface.h"
#include "wifi_udp.h"
#include "setuppage.h"
#include "display.h"
#include "filesys.h"

void setup() {
  Serial.begin(115200);
  littlefsinit();
  wifi_init();
  displayinit();
  setuppage_init();
}

void loop() {
  display();
  wifi_receive();
  setuppage_func();
}

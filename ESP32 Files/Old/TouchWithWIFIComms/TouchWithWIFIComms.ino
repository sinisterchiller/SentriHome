#include "tft_interface.h"
#include "wifi_udp.h"

void setup() {
  wifi_init();
  display_init();
  
}

void loop() {
  display_func();
  wifi_receive();
  
}

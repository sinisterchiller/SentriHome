#include "api.h"

WebServer server(80);

void sendalert(String message){
    HTTPClient http;
    http.begin("http://192.168.10.1/api/module");
    http.addHeader("Content-Type", "application/x-www-form-urlencoded");
    http.POST(message);
    http.end();
}
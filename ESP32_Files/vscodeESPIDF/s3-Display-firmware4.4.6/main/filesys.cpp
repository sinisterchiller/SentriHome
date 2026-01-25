#include "filesys.h"

void littlefsinit(){
    if (!LittleFS.begin(true)) {   
    Serial.println("LittleFS mount failed");
    return;
  }
  Serial.println("LittleFS mounted");
  littlefsWriteFile("wifissid.txt", "generic");
  littlefsWriteFile("wifipass.txt", "generic");
}

void littlefsWriteFile(String filename, String content){
    File file = LittleFS.open(filename, "w");
    file.print(content);
    file.close();
}

String littlefsReadFile(String filename){
    File file = LittleFS.open(filename);
    String content = file.readString();
    file.close();
    return content;
}
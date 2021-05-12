/*
   MIT License

   Copyright (c) 2021 Felix Biego

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in all
   copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
   SOFTWARE.
*/
#include <Update.h>
#include "FS.h"
#include "SPIFFS.h"
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>



#define BUILTINLED 2
#define FORMAT_SPIFFS_IF_FAILED true

uint8_t major = 1;
uint8_t minor = 3;
uint8_t ver[] = {0xFA, major, minor};  // code version

uint8_t updater[16384]; // >= MainActivity.PART

#define SERVICE_UUID              "fb1e4001-54ae-4a28-9f74-dfccb248601d"
#define CHARACTERISTIC_UUID_RX    "fb1e4002-54ae-4a28-9f74-dfccb248601d"
#define CHARACTERISTIC_UUID_TX    "fb1e4003-54ae-4a28-9f74-dfccb248601d"

static BLECharacteristic* pCharacteristicTX;
static BLECharacteristic* pCharacteristicRX;

static bool deviceConnected = false;
static int id = 0;
static bool updating = false;
static bool request = false;
static int parts = 0;
static int next = 0;
static int cur = 0;
static int MTU = 0;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;

    }
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      id = 0;
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {

    //    void onStatus(BLECharacteristic* pCharacteristic, Status s, uint32_t code) {
    //      Serial.print("Status ");
    //      Serial.print(s);
    //      Serial.print(" on characteristic ");
    //      Serial.print(pCharacteristic->getUUID().toString().c_str());
    //      Serial.print(" with code ");
    //      Serial.println(code);
    //    }

    void onNotify(BLECharacteristic *pCharacteristic) {
      uint8_t* pData;
      std::string value = pCharacteristic->getValue();
      int len = value.length();
      pData = pCharacteristic->getData();
      if (pData != NULL) {
        //        Serial.print("Notify callback for characteristic ");
        //        Serial.print(pCharacteristic->getUUID().toString().c_str());
        //        Serial.print(" of data length ");
        //        Serial.println(len);
        Serial.print("TX  ");
        for (int i = 0; i < len; i++) {
          Serial.printf("%02X ", pData[i]);
        }
        Serial.println();
      }
    }

    void onWrite(BLECharacteristic *pCharacteristic) {
      uint8_t* pData;
      std::string value = pCharacteristic->getValue();
      int len = value.length();
      pData = pCharacteristic->getData();
      if (pData != NULL) {
        //        Serial.print("Write callback for characteristic ");
        //        Serial.print(pCharacteristic->getUUID().toString().c_str());
        //        Serial.print(" of data length ");
        //        Serial.println(len);
        Serial.print("RX  ");
        for (int i = 0; i < len; i++) {
          Serial.printf("%02X ", pData[i]);
        }
        Serial.println();

        if (pData[0] == 0xFB) {
          int pos = pData[1];
          for (int x = 0; x < len - 2; x++) {
            updater[(pos * MTU) + x] = pData[x + 2];
          }
          
        } else if  (pData[0] == 0xFC) {
          int len = (pData[1] * 256) + pData[2];
          cur = (pData[3] * 256) + pData[4];
          writeBinary(SPIFFS, "/update.bin", updater, len);
          if (cur < parts - 1) {
            request = true;
          }
        } else if (pData[0] == 0xFD) {
          SPIFFS.format();
        } else if  (pData[0] == 0xF0) {

        } else if  (pData[0] == 0xFE) {
          rebootEspWithReason("Rebooting to start OTA update");
        } else if  (pData[0] == 0xFF) {
          parts = (pData[1] * 256) + pData[2];
          MTU = (pData[3] * 256) + pData[4];
          updating = true;
        }


      }

    }

    void writeBinary(fs::FS &fs, const char * path, uint8_t *dat, int len) {

      //Serial.printf("Write binary file %s\r\n", path);

      File file = fs.open(path, FILE_APPEND);

      if (!file) {
        Serial.println("- failed to open file for writing");
        return;
      }
      file.write(dat, len);
      file.close();
    }



    void rebootEspWithReason(String reason) {
      Serial.println(reason);
      delay(1000);
      ESP.restart();
    }




};



void initBLE() {
  BLEDevice::init("ESP32 OTA");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  pCharacteristicTX = pService->createCharacteristic(CHARACTERISTIC_UUID_TX, BLECharacteristic::PROPERTY_NOTIFY );
  pCharacteristicRX = pService->createCharacteristic(CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  pCharacteristicRX->setCallbacks(new MyCallbacks());
  pCharacteristicTX->setCallbacks(new MyCallbacks());
  pCharacteristicTX->addDescriptor(new BLE2902());
  pCharacteristicTX->setNotifyProperty(true);
  pService->start();


  // BLEAdvertising *pAdvertising = pServer->getAdvertising();  // this still is working for backward compatibility
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connections issue
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  Serial.println("Characteristic defined! Now you can read it in your phone!");
}

void setup() {
  Serial.begin(115200);
  Serial.println("Starting BLE work!");
  pinMode(BUILTINLED, OUTPUT);

  if (!SPIFFS.begin(FORMAT_SPIFFS_IF_FAILED)) {
    Serial.println("SPIFFS Mount Failed");
    return;
  }

  updateFromFS(SPIFFS);

  initBLE();

}

void loop() {




  if (deviceConnected) {
    digitalWrite(BUILTINLED, HIGH);

    if (id < 512) {
      if (id >= 510) {
        pCharacteristicTX->setValue(ver, 3);
        pCharacteristicTX->notify();
        delay(100);
      }
      id++;
    }
    if (updating) {
      if (request) {
        uint8_t rq[] = {0xF1, (cur + 1) / 256, (cur + 1) % 256};
        pCharacteristicTX->setValue(rq, 3);
        pCharacteristicTX->notify();
        delay(100);
        request = false;
      }
      if (cur+1 == parts){
        uint8_t com[] = {0xF2, (cur + 1) / 256, (cur + 1) % 256};
        pCharacteristicTX->setValue(com, 3);
        pCharacteristicTX->notify();
        delay(100);
        updating = false;
      }
    }
  } else {
    digitalWrite(BUILTINLED, LOW);
  }

}




void performUpdate(Stream &updateSource, size_t updateSize) {
  if (Update.begin(updateSize)) {
    size_t written = Update.writeStream(updateSource);
    if (written == updateSize) {
      Serial.println("Written : " + String(written) + " successfully");
    }
    else {
      Serial.println("Written only : " + String(written) + "/" + String(updateSize) + ". Retry?");
    }
    if (Update.end()) {
      Serial.println("OTA done!");
      if (Update.isFinished()) {
        Serial.println("Update successfully completed. Rebooting.");
      }
      else {
        Serial.println("Update not finished? Something went wrong!");
      }
    }
    else {
      Serial.println("Error Occurred. Error #: " + String(Update.getError()));
    }

  }
  else
  {
    Serial.println("Not enough space to begin OTA");
  }
}

void updateFromFS(fs::FS &fs) {
  File updateBin = fs.open("/update.bin");
  if (updateBin) {
    if (updateBin.isDirectory()) {
      Serial.println("Error, update.bin is not a file");
      updateBin.close();
      return;
    }

    size_t updateSize = updateBin.size();

    if (updateSize > 0) {
      Serial.println("Try to start update");
      performUpdate(updateBin, updateSize);
    }
    else {
      Serial.println("Error, file is empty");
    }

    updateBin.close();

    // when finished remove the binary from sd card to indicate end of the process
    fs.remove("/update.bin");
    Serial.println("Removing update file");
    delay(1000);
    ESP.restart();
  }
  else {
    Serial.println("Could not load update.bin from sd root");
  }
}

# ESP32_BLE_OTA_Android
Android app to perform OTA update on ESP32 via BLE

[`BLE-OTA-v1.4.apk`](https://github.com/fbiego/ESP32_BLE_OTA_Android/raw/master/app/release/BLE-OTA-v1.4.apk)

## Arduino
 [`ESP32 Code`](https://github.com/fbiego/ESP32_BLE_OTA_Android/blob/master/esp32_ota/esp32_ota.ino)

## Transfer Sequence
1. Choose the binary file to be sent
2. Split the file into parts (16384 bytes each)
3. Send a command to format the SPIFFS 
4. Send a command specifying the number of parts and MTU size
5. Send the first part to the board
6. Send a command indicating completion of the part along with the number of bytes (this will trigger writing to the update.bin file along with a request to the next part)
7. Receive request of a part specifying the part number (repeat 5, 6 & 7)
8. After all parts have been sent trigger a restart in order to apply the update


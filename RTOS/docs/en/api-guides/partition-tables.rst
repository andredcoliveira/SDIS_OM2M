Partition Tables
================

Overview
--------

A single ESP8266's flash can contain multiple apps, as well as many different kinds of data (calibration data, filesystems, parameter storage, etc). For this reason a partition table is flashed to offset 0x8000 in the flash.

Partition table length is 0xC00 bytes (maximum 95 partition table entries). An MD5 checksum is appended after the table data.

Each entry in the partition table has a name (label), type (app, data, or something else), subtype and the offset in flash where the partition is loaded.

The simplest way to use the partition table is to `make menuconfig` and choose one of the simple predefined partition tables:

* "Single factory app, no OTA"
* "Two OTA app"

If you `make partition_table` then it will print a summary of the partition table.

Built-in Partition Tables
-------------------------

Here is the summary printed for the "Single factory app, no OTA" configuration::

  # Espressif ESP8266 Partition Table
  # Name,   Type, SubType, Offset,  Size
  nvs,      data, nvs,     0x9000,  0x6000
  phy_init, data, phy,     0xf000,  0x1000
  factory,  app,  factory, 0x10000, 0xF0000

* At a 0x10000 (64KB) offset in the flash is the app labelled "factory". The bootloader will run this app by default.
* There are also two data regions defined in the partition table for storing NVS library partition and PHY init data.

Here is the summary printed for the "Two OTA definitions" configuration::

  # Espressif ESP8266 Partition Table
  # Name,   Type, SubType, Offset,   Size
  nvs,      data, nvs,     0x9000,   0x4000
  otadata,  data, ota,     0xd000,   0x2000
  phy_init, data, phy,     0xf000,   0x1000
  ota_0,    0,    ota_0,   0x10000,  0xF0000
  ota_1,    0,    ota_1,   0x110000, 0xF0000

* There are now two app partition definitions, ota_0 at 0x10000 and ota_1 at 0x110000
* There is also a new "ota data" slot, which holds the data for OTA updates. The bootloader consults this data in order to know which app to execute. If "ota data" is empty, it will execute the ota_0 app.

Creating Custom Tables
----------------------

If you choose "Custom partition table CSV" in menuconfig then you can also enter the name of a CSV file (in the project directory) to use for your partition table. The CSV file can describe any number of definitions for the table you need.

The CSV format is the same format as printed in the summaries shown above. However, not all fields are required in the CSV. For example, here is the "input" CSV for the OTA partition table::

  # Name,   Type, SubType, Offset,   Size
  nvs,      data, nvs,     0x9000,   0x4000
  otadata,  data, ota,     0xd000,   0x2000
  phy_init, data, phy,     0xf000,   0x1000
  ota_0,    app,  ota_0,   0x10000,  0xF0000
  ota_1,    app,  ota_1,   0x110000, 0xF0000

* Whitespace between fields is ignored, and so is any line starting with # (comments).
* Each non-comment line in the CSV file is a partition definition.
* Only the offset for the first partition is supplied. The gen_esp32part.py tool fills in each remaining offset to start after the preceding partition.

Name field
~~~~~~~~~~

Name field can be any meaningful name. It is not significant to the ESP8266. Names longer than 16 characters will be truncated.

Type field
~~~~~~~~~~

Partition type field can be specified as app (0) or data (1). Or it can be a number 0-254 (or as hex 0x00-0xFE). Types 0x00-0x3F are reserved for ESP8266_RTOS_SDK core functions.

If your application needs to store data, please add a custom partition type in the range 0x40-0xFE.

The bootloader ignores any partition types other than app (0) & data (1).

Subtype
~~~~~~~

The 8-bit subtype field is specific to a given partition type.

ESP8266_RTOS_SDK currently only specifies the meaning of the subtype field for "app" and "data" partition types.

App Subtypes
~~~~~~~~~~~~

When type is "app", the subtype field can be specified as ota_0 (0x10), ota_1 (0x11) ... ota_15 (0x1F) or test (0x20).

- ota_0 (0x10) is the default app partition. The bootloader will execute the ota_0 app unless there it sees another partition of type data/ota, in which case it reads this partition to determine which OTA image to boot.

- ota_0 (0x10) ... ota_15 (0x1F) are the OTA app slots. If using OTA, an application should have at least two OTA application slots (ota_0 & ota_1).

Data Subtypes
~~~~~~~~~~~~~

When type is "data", the subtype field can be specified as ota (0), phy (1), nvs (2).

- ota (0) is the **OTA data partition** which stores information about the currently selected OTA application. This partition should be 0x2000 bytes in size. Refer to the **OTA documentation** for more details.
- phy (1) is for storing PHY initialisation data. This allows PHY to be configured per-device, instead of in firmware.

  - In the default configuration, the phy partition is not used and PHY initialisation data is compiled into the app itself. As such, this partition can be removed from the partition table to save space.
  - To load PHY data from this partition, run ``make menuconfig`` and enable **ESP_PHY_INIT_DATA_IN_PARTITION** option. You will also need to flash your devices with phy init data as the ESP8266_RTOS_SDK build system does not do this automatically.
- nvs (2) is for the **Non-Volatile Storage (NVS) API**.

  - NVS is used to store per-device PHY calibration data (different to initialisation data).
  - NVS is used to store WiFi data if the **esp_wifi_set_storage(WIFI_STORAGE_FLASH)** initialisation function is used.
  - The NVS API can also be used for other application data.
  - It is strongly recommended that you include an NVS partition of at least 0x3000 bytes in your project.
  - If using NVS API to store a lot of data, increase the NVS partition size from the default 0x6000 bytes.

Other data subtypes are reserved for future ESP8266_RTOS_SDK uses.

Offset & Size
~~~~~~~~~~~~~

Please note that the app partition must fall in only one integrated partition of 1M. Otherwise, the application crashes.

The starting address of firmware is configured to 0x10000 by default. If you want to change the starting address of firmware, please:

- Configure the value in `menu -> partition table -> select "Custom partition table CSV" -> (0x10000) Factory app partition offset`;
- Configure the ota_1 offset in the CSV file of partition table to the value , and ota_2 offset to the mirror value (ota_2 = ota_1 + 0x100000).
	
  - Please enter an aligned offset. Otherwise, the tool will return errors.	
  - Don't leave it blank, because, in this case, the tool will automatically align the app partition, which may cause app partition overlaps. That said, the app partition falls in more than one integrated partitions of 1M. 

Sizes and offsets can be specified as decimal numbers, hex numbers with the prefix 0x, or size multipliers K or M (1024 and 1024*1024 bytes).

Generating Binary Partition Table
---------------------------------

The partition table which is flashed to the ESP8266 is in a binary format, not CSV. The tool **partition_table/gen_esp32part.py** is used to convert between CSV and binary formats.

If you configure the partition table CSV name in ``make menuconfig`` and then ``make partition_table``, this conversion is done as part of the build process.

To convert CSV to Binary manually::

  python gen_esp32part.py --verify input_partitions.csv binary_partitions.bin

To convert binary format back to CSV::

  python gen_esp32part.py --verify binary_partitions.bin input_partitions.csv

To display the contents of a binary partition table on stdout (this is how the summaries displayed when running `make partition_table` are generated::

  python gen_esp32part.py binary_partitions.bin

``gen_esp32part.py`` takes one optional argument, ``--verify``, which will also verify the partition table during conversion (checking for overlapping partitions, unaligned partitions, etc.)

Flashing the partition table
----------------------------

* ``make partition_table-flash``: will flash the partition table with esptool.py.
* ``make flash``: Will flash everything including the partition table.

A manual flashing command is also printed as part of ``make partition_table``.

Note that updating the partition table doesn't erase data that may have been stored according to the old partition table. You can use ``make erase_flash`` (or ``esptool.py erase_flash``) to erase the entire flash contents.
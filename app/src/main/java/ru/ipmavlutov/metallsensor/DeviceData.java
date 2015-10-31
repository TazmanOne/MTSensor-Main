package ru.ipmavlutov.metallsensor;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

/**
 * Created by user on 05.08.2015.
 */

    public class DeviceData {
        private String name = "";
        private String address = "";
        private int bondState = BluetoothDevice.BOND_NONE;
        public UUID uuids = null;
        private final int deviceClass;
        private final int majorDeviceClass;

        public DeviceData(BluetoothDevice device, String emptyName) {
            name = device.getName();
            address = device.getAddress();
            bondState = device.getBondState();

            if (name == null || name.isEmpty()) name = emptyName;
            deviceClass = device.getBluetoothClass().getDeviceClass();
            majorDeviceClass = device.getBluetoothClass().getMajorDeviceClass();
            uuids = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        }

        public String getName() {
            return name;
        }

        public void setName(String deviceName) {
            name = deviceName;
        }

        public String getAddress() {
            return address;
        }

        public int getDeviceClass() {
            return deviceClass;
        }

        public int getMajorDeviceClass() {
            return majorDeviceClass;
        }

        public void setBondState(int state) {
            bondState = state;
        }

        /*public ArrayList<ParcelUuid> getUuids() {
            return uuids;
        }*/

        public int getBondState() {
            return bondState;
        }
    }


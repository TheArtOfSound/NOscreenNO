package com.qevcrypt.app;

import android.app.admin.DeviceAdminReceiver;

/**
 * Device-admin / device-owner entry point. When noscreeno is provisioned as the
 * device owner (via QR enrollment or `adb shell dpm set-device-owner`), this lets
 * it apply device-wide policy — block screen capture, disable the camera, enforce
 * lock — through DevicePolicyManager.
 */
public class QevDeviceAdmin extends DeviceAdminReceiver {
}

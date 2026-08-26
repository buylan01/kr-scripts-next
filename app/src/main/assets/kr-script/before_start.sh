#!/system/bin/sh

# Install BusyBox
if [ ! -f "$TOOLKIT/busybox_installed" ]; then
    . "$TOOLKIT"/busybox_install
fi

echo "Android Version: $(getprop ro.build.version.release)"
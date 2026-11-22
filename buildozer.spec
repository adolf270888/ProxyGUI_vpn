[app]
title = ProxyGUI
package.name = proxygui
package.domain = org.proxygui
source.dir = .
source.include_exts = py,png,jpg,kv,txt
version = 1.1
requirements = python3,kivy,certifi,pyjnius
orientation = portrait
fullscreen = 0
android.extra_manifest_application_arguments = manifest_args.txt
android.extra_manifest_application_xml = src/android/extra_manifest_application.xml
android.add_src = src/android
android.add_aars = libs/hev-socks5-tunnel.aar
android.permissions = INTERNET,POST_NOTIFICATIONS,FOREGROUND_SERVICE,FOREGROUND_SERVICE_SPECIAL_USE
android.api = 34
android.build_tools_version = 34.0.0
android.accept_sdk_license = True
android.minapi = 24
android.archs = arm64-v8a,armeabi-v7a
presplash.color = #1e1e24
window.resize_with_keyboard = 1
p4a.branch = v2024.01.21

[buildozer]
log_level = 2
warn_on_root = 1

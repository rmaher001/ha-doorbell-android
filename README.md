# Home Assistant Doorbell Android App

A custom Android app that provides full-screen doorbell notifications for Home Assistant, similar to incoming phone calls.

## Features

- **Full-Screen Intent Notifications**: Takes over your phone screen when doorbell rings (even when locked)
- **Live Camera Feed**: Displays your doorbell camera stream via WebView
- **AI Visitor Analysis**: Shows AI-powered visitor identification from Home Assistant
- **Door Unlock**: Quick unlock button integrated with Home Assistant lock entity
- **Background Monitoring**: Foreground service maintains WebSocket connection to Home Assistant
- **Secure Storage**: Encrypted credentials using Android Security Crypto library
- **Auto-Dismiss**: Configurable timeout to automatically close the notification

## Requirements

- Android 10+ (API 29 or higher)
- Home Assistant instance with:
  - WebSocket API access
  - Long-lived access token
  - Doorbell binary sensor
  - Lock entity (optional)
  - Camera entity

## Installation

### Via ADB
```bash
adb install app-debug.apk
```

### From Source
1. Clone this repository
2. Open in Android Studio
3. Build and run on your device

## Configuration

1. Open the app and enter:
   - **Home Assistant URL**: Your HA instance URL (e.g., `https://home.example.com`)
   - **Access Token**: Long-lived access token from HA
   - **Doorbell Entity**: Binary sensor entity ID (e.g., `binary_sensor.doorbell_visitor`)
   - **Lock Entity**: Lock entity ID (e.g., `lock.front_door_lock`)
   - **Camera URL**: Camera proxy path (e.g., `/api/camera_proxy/camera.doorbell_clear`)

2. Test your connection

3. Grant required permissions:
   - Notifications
   - Display over other apps
   - Full-screen intent

4. Start the monitoring service

## Architecture

### Core Components

- **MainActivity**: Settings and configuration UI
- **DoorbellMonitorService**: Background service monitoring Home Assistant WebSocket
- **DoorbellActivity**: Full-screen UI that appears on doorbell trigger
- **HomeAssistantClient**: WebSocket and REST API client
- **PreferencesManager**: Secure credential storage
- **NotificationHelper**: Notification channel management

### Permissions

- `USE_FULL_SCREEN_INTENT`: Required for lock screen takeover
- `FOREGROUND_SERVICE`: Background monitoring service
- `POST_NOTIFICATIONS`: Notification delivery
- `WAKE_LOCK`: Screen wake on doorbell trigger
- `SYSTEM_ALERT_WINDOW`: Display over other apps
- `INTERNET`: Network communication

## Integration with Home Assistant

The app monitors a binary sensor entity for doorbell events. When the sensor state changes to "on", it:

1. Triggers a full-screen notification
2. Displays live camera feed
3. Fetches AI analysis from automation attributes
4. Provides unlock button for door control

### Example Home Assistant Automation

```yaml
automation:
  - alias: "Doorbell Notification"
    trigger:
      - platform: state
        entity_id: binary_sensor.doorbell_visitor
        to: "on"
    action:
      - service: llmvision.stream_analyzer
        data:
          # ... AI analysis configuration
```

## Development

Built with:
- Kotlin
- Material Design 3
- OkHttp (WebSocket client)
- Gson (JSON parsing)
- Kotlin Coroutines
- Android Security Crypto

## License

MIT License

## Contributing

Issues and pull requests welcome!

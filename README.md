# Claude Code Mobile

An Android client for [Claude Code UI](https://github.com/siteboon/claudecodeui) (CloudCLI).

Connect to your self-hosted CloudCLI server or CloudCLI Cloud from your Android device.

## Features

- **WebView Wrapper** - Native Android shell around the CloudCLI web UI
- **Multiple Servers** - Save and switch between multiple server configurations
- **File Upload** - Full support for file uploads through the web interface
- **Pull to Refresh** - Swipe down to reload the current page
- **Dark/Light Theme** - Follows system theme automatically
- **SSL Trust Options** - Enable trust for self-signed certificates on local dev servers
- **Cookie Persistence** - Stay logged in across app restarts
- **Back Navigation** - Hardware back button navigates within WebView history

## Screenshots

| Main View | Server Settings | Add Server |
|---|---|---|
| WebView with toolbar | Server list | Server form |

## Quick Start

### Download

Download the latest APK from [GitHub Releases](../../releases).

### Build from Source

```bash
# Clone the repository
git clone https://github.com/djsevenx1/claude-code-mobile.git
cd claude-code-mobile

# Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Configuration

1. Launch the app
2. Open Settings (menu > Settings)
3. Tap the + button to add a server
4. Enter your server URL (e.g., `http://192.168.1.100:3001` or `https://cloudcli.ai`)
5. Set the server as default
6. Return to the main screen

### Setting up CloudCLI Server

Follow the [CloudCLI documentation](https://cloudcli.ai/docs) to run a server:

```bash
# Using npx (requires Node.js v22+)
npx @cloudcli-ai/cloudcli

# Or install globally
npm install -g @cloudcli-ai/cloudcli
cloudcli
```

Then connect from the app using your machine's IP address and port 3001.

## Tech Stack

- **Kotlin** - 100% Kotlin
- **AndroidX** - Core libraries
- **Material Design 3** - UI components
- **WebView** - Web content rendering
- **SQLite** - Server configuration storage
- **ViewBinding** - Type-safe view access
- **GitHub Actions** - CI/CD for APK builds

## Project Structure

```
app/src/main/java/com/claudecode/mobile/
├── ClaudeCodeApp.kt          # Application class
├── MainActivity.kt           # WebView host activity
├── SettingsActivity.kt       # Server management
├── AddServerActivity.kt      # Add/edit server form
├── data/
│   ├── ServerConfig.kt       # Server data model
│   └── ServerRepository.kt   # SQLite storage
└── web/
    └── CloudWebViewClient.kt # WebView client with error handling
```

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1) or later
- JDK 17
- Android SDK 34

### Release Build

```bash
# Generate a signing keystore
keytool -genkey -v -keystore release.keystore -alias claudecode \
  -keyalg RSA -keysize 2048 -validity 10000

# Build release APK
./gradlew assembleRelease

# Sign the APK
apksigner sign --ks release.keystore \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

### GitHub Actions

The repository includes a GitHub Actions workflow (`.github/workflows/build.yml`) that:
1. Builds debug and release APKs on tag push
2. Signs the release APK (if signing secrets are configured)
3. Creates a GitHub Release with the APK attached

To configure release signing, add these repository secrets:
- `SIGNING_KEY` - Base64-encoded keystore file
- `KEY_ALIAS` - Keystore alias
- `KEY_STORE_PASSWORD` - Keystore password
- `KEY_PASSWORD` - Key password

## License

This project is licensed under the MIT License.

## Acknowledgments

- [Claude Code UI](https://github.com/siteboon/claudecodeui) by siteboon
- [Claude Code](https://docs.anthropic.com/en/docs/claude-code) by Anthropic

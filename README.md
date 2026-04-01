# Qlub Auto Translate

A Gradle plugin that automatically translates missing Android string resources using OpenAI's API.

## Features

- Automatically detects missing translations across all modules
- Uses OpenAI API for high-quality, context-aware translations
- Processes translations in batches for efficiency
- Preserves placeholders, HTML tags, and special characters
- Supports multiple languages simultaneously
- Auto-runs on release builds (optional)
- Reads API key from `local.properties` or environment variable
- Gradle Configuration Cache compatible
- Detailed logging with optional verbose mode

## Installation

Add the plugin to your project's `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.clubpay.auto-translate") version "1.2.1"
}
```

## Configuration

Configure the plugin in your `build.gradle.kts`:

```kotlin
qlubAutoTranslate {
    appContext = """
        Your app description here.
        This helps the AI provide more accurate, context-aware translations.
    """.trimIndent()
    verbose = false  // Set to true for detailed logs
    model = "gpt-5-nano"  // Optional: specify OpenAI model
}
```

## API Key Setup

The plugin looks for the OpenAI API key in this order:

1. **`local.properties`** (recommended) - add to your project root:
   ```properties
   OPENAI_API_KEY=sk-your-api-key
   ```
2. **Environment variable**:
   ```bash
   export OPENAI_API_KEY=sk-your-api-key
   ```
3. **Extension** (not recommended - avoid committing secrets):
   ```kotlin
   qlubAutoTranslate {
       apiKey = "sk-your-api-key"
   }
   ```

> **Note:** `local.properties` is already in `.gitignore` in standard Android projects, so your API key stays safe.

## Usage

### Translate to Multiple Languages

```bash
./gradlew :app:qlubAutoTranslate -Plangs=tr,de,fr,es
```

### Translate to Single Language

```bash
./gradlew :app:qlubAutoTranslate -Plang=tr
```

### Auto-translate on Release Builds

Configure target languages and enable auto-run:

```kotlin
qlubAutoTranslate {
    targetLanguages = listOf("ar", "zh-rHK", "tr")
    runOnReleaseBuild = true
    appContext = "Your app description"
}
```

When enabled, the plugin automatically runs before any release build (`assembleXxxRelease`, `bundleXxxRelease`). If no translations are missing, it skips silently with no API cost.

## How It Works

1. Scans all modules for default string resources (`values/strings.xml`)
2. Identifies missing translations in target language folders (e.g., `values-tr/strings.xml`)
3. Sends missing strings to OpenAI API in batches of 50
4. Automatically adds translated strings to the appropriate language files
5. Creates missing directories and files as needed

## Supported Languages

`de`, `fr`, `es`, `tr`, `zh-rHK`, `zh-rSG`, `ko`, `ja`, `it`, `pt`, `ru`, `ar`

## Requirements

- Gradle 8.0+
- OpenAI API key
- Android project with standard resource structure

## Example Output

```
Target languages: tr, de
Found 3 modules with res directories

Total missing keys to translate: 45

Translating in batches of 50 keys per language...
[tr] 1 batch(es) to translate (max 50 per batch)
[tr] -> Sending batch 1/1 with 45 keys
Using AI model: gpt-5-nano

============================================================
Batch translation applied
Modules touched: 2
Total translations added: 45
============================================================
```

## Notes

- The plugin preserves Android string formatting (placeholders, HTML tags, escape sequences)
- Brand names and technical IDs are not translated
- Translations are context-aware based on your app description
- Always review AI-generated translations before shipping

## Developed by Qlub

Open source project by [Qlub](https://qlub.io)

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

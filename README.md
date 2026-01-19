![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/coreply/cooperate/total)
![GitHub Tag](https://img.shields.io/github/v/tag/coreply/cooperate)
![GitHub License](https://img.shields.io/github/license/coreply/cooperate)

![Cooperate banner](./docs/static/cooperate_banner.png)

# ⚠️Disclaimer

**This project is in early development stage. It is only intended to demonstrate the abilities of LLMs
operating smartphones. You are giving the app extensive permissions, including reading your
screen content and operating on your behalf. I am not liable for any costs, damages or data loss
that may
occur from using this app. Please use at your own risk.**

---

**Cooperate** is an open-source Android app giving large language models (LLMs) the ability to
interact with Android apps. Currently, only some Claude models are tested.

## Tested on these models via Openrouter

| Model                 | Estimated cost per step* |
|-----------------------|--------------------------|
| **Claude 4.5 Haiku**  | $0.003-$0.005            |
| **Clause 4.5 Sonnet** | $0.01-$0.015             |
| **Claude 4 Sonnet**   | $0.01-$0.015             |

* Each step means sending one request to the model, that normally results in performing one action
  such as clicking or going back. Costs estimations are for reference only. Your bill could go up
  high easily. I am not responsible for any charges incurred by using this app.

## Features

You enter a prompt, the app sends your prompt and the current screenshot to the LLM, then
performs whatever action the LLM requests, and repeats the process until the LLM no longer
requests any action.

## Safety

A stop button is displayed at the top of the screen when the app is executing the task. The button
is a kill switch that calls `disableSelf()` on the accessibility service. By clicking on that, the
app should immediately lose the ability to control your device. **You must re-enable the
accessibility service manually to start a new task.**

In extremely unlikely circumstances where the
button is not working and you want to stop the app, try to:

- Uninstall the app
- Turn on airplane mode
- Restart your phone
- Cut off internet connection by removing SIM card or turning off your router.

## Getting Started

### Prerequisites

- Device running **Android 11 or higher**
- API key for an OpenAI-compatible API endpoint inference service, such
  as [Openrouter](https://openrouter.ai/)

### Build From Source

1. Clone the repository:
2. Open the project in Android Studio.
3. Sync the Gradle files and resolve any dependencies.
4. Build and run the app on your preferred device or emulator.

## Setup with Openrouter

1. Get your API Keys [here](https://openrouter.ai/settings/keys)
2. Set the API Endpoint to `https://openrouter.ai/api/v1` and the model name
   to `anthropic/claude-haiku-4.5`, `anthropic/claude-sonnet-4.5`, `anthropic/claude-sonnet-4` or other models you wish to test with.
3. Set the API Key to the key you got in step 1.

## Contributing

Currently the code is messy and not well documented. Please open an issue and discuss in advance
before making contributions.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=coreply/cooperate&type=Date)](https://www.star-history.com/#coreply/coreply&Date)

## License Notice

Cooperate

Copyright (C) 2025 Cooperate

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.

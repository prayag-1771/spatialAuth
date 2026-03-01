🔐 Authenticator
Environment-as-Identity Authentication System
📌 Overview

Authenticator is a space-aware authentication system that generates a unique Room Signature using environmental sensor data.

Instead of relying only on passwords or biometrics, this project introduces a new security factor:

🏠 Something you are inside of.

Each indoor space has a distinct combination of acoustic reflections, WiFi signal patterns, magnetic distortions, and environmental characteristics. Authenticator extracts these features to create a secure spatial signature that can be used for authentication and smart access control.

🚀 Problem

Traditional authentication systems:

Can be stolen (passwords)

Can be spoofed (biometrics)

Can be phished (OTP)

But physical environments are extremely difficult to replicate precisely.

We leverage the unique fingerprint of a room as an additional authentication layer.

💡 Core Concept

Room → Sensor Data → Feature Extraction → Signature Vector → Secure Hash

The generated hash acts as a Room Authentication Key (RAK).

🛠 How It Works (MVP Flow)

Capture environmental data (WiFi RSSI, acoustic sample, sensor readings)

Extract distinguishing features

Generate a normalized signature vector

Hash the signature

Compare with registered room baseline

Return similarity score / authentication result

🌍 Potential Use Cases

🔒 Location-bound login systems

🏠 Smart home secure activation

🧪 Secure lab or exam hall authentication

🛡 Insider threat detection

📱 IoT device authorization

🧪 Hackathon Scope (Current Stage)

Basic sensor data capture

Signature generation

Similarity-based authentication

Demo login simulation

🔮 Future Scope

AI-enhanced signature modeling

Continuous passive authentication

Multi-room clustering

Edge-optimized real-time processing

Secure zone enforcement

👥 Team

Team Name: Authenticator
Tagline: Win : Null

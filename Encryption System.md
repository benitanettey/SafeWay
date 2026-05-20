---
tags:
  - security
  - encryption
  - keystore
---
# Encryption System

**File:** `com.example.safeway.EncryptionManager`

All sensitive incident fields are encrypted at rest using **AES-256/GCM/NoPadding** via the **Android Keystore** system.

## Architecture

The `EncryptionManager` is a singleton `object` that wraps the Android Keystore API.

```
Plaintext → AES/GCM/NoPadding (256-bit) → Base64(IV + Ciphertext)
```

### Key Management

- **Key Alias:** `safeway_incident_key`
- **Storage:** Android Keystore (hardware-backed on supported devices)
- **Algorithm:** AES (256-bit)
- **Block Mode:** GCM (with 128-bit tag)
- **IV Length:** 12 bytes
- **Purpose:** ENCRYPT + DECRYPT

The key is created once and stored in the Android Keystore, surviving app restarts and device reboots. It cannot be extracted.

## Encryption Flow

```kotlin
EncryptionManager.encrypt("Physical")   // → "j4kS8d...=="
EncryptionManager.decrypt("j4kS8d...==") // → "Physical"
```

### How it works

1. Retrieve or generate the AES-256 key from Keystore
2. Cipher in encrypt mode → get a random 12-byte IV
3. Encrypt plaintext bytes → get ciphertext
4. Concatenate: `IV + Ciphertext`
5. Base64 encode the combined byte array

### Decryption (self-detecting)

`isEncrypted()` heuristic: checks if the string is long enough (>24 chars) and matches Base64 pattern. If not encrypted, passes through unchanged. This allows mixed encrypted/plaintext to coexist.

## Where Encryption Is Applied

Encryption happens in [[Incident Logging]] (saveIncident) and decryption in [[Records & Export]]:

```kotlin
// Encryption on save
Incident(
    type = EncryptionManager.encrypt(selectedType),
    description = EncryptionManager.encrypt(description),
    severity = EncryptionManager.encrypt(selectedSeverity),
    location = EncryptionManager.encrypt(location),
    who = EncryptionManager.encrypt(who)
)

// Decryption on read (RecordsActivity.decryptIncident)
incident.copy(
    type = EncryptionManager.decrypt(incident.type),
    description = EncryptionManager.decrypt(incident.description),
    // ...
)
```

### Encryption in Exports

The "Encrypted Export" feature (`.enc` files) uses a **separate** encryption scheme:

- **Key derivation:** PBKDF2WithHmacSHA256 (10,000 iterations)
- **Password:** Hardcoded `"SafeWayExportSecret"` 
- **Salt:** 16 random bytes
- **IV:** 12 random bytes
- **Output format:** `salt[16] + iv[12] + ciphertext`

> ⚠️ Note: The export encryption uses a hardcoded passphrase — not derived from the Keystore key.

## Related

- [[Incident (Entity)]] — Fields that are encrypted
- [[Data Layer]] — How encrypted data is stored
- [[Records & Export]] — Encrypted export format

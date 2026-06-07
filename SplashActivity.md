---
tags:
  - activity
  - splash
---
# SplashActivity

**File:** `com.example.safeway.SplashActivity`

The app's launch screen.

## Behavior

1. Shows splash layout (`activity_splash.xml`) for **1.5 seconds**
2. Transitions to [[HomeActivity]] with `CLEAR_TASK | NEW_TASK` flags
3. Uses `fade_in` animation for both enter and exit

```kotlin
Handler(Looper.getMainLooper()).postDelayed({
    startActivity(Intent(this, HomeActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    overridePendingTransition(R.anim.fade_in, R.anim.fade_in)
}, 1500)
```

The `CLEAR_TASK` flag ensures the splash is removed from the back stack so the user can't navigate back to it.

## Related

- [[HomeActivity]] — Destination after splash
- [[Navigation System]] — Activity flow

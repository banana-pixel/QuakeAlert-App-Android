# Quick Integration Guide

## Steps to Integrate Telegram-style Chat

### 1. Basic Integration (Recommended)

Replace your current chat fragment in your navigation or activity:

**Before:**
```kotlin
// In your Activity or Fragment
supportFragmentManager.beginTransaction()
    .replace(R.id.chat_container, ChatFragment())
    .commit()
```

**After:**
```kotlin
supportFragmentManager.beginTransaction()
    .replace(R.id.chat_container, TelegramChatFragment())
    .commit()
```

### 2. Navigation Component Integration

If using Navigation Component, update your nav graph:

**nav_graph.xml:**
```xml
<fragment
    android:id="@+id/chatFragment"
    android:name="id.my.bananapixel.quakealert.ui.TelegramChatFragment"
    android:label="Chat"
    tools:layout="@layout/fragment_chat_telegram" />
```

### 3. ViewBinding Migration

If migrating from existing ChatFragment:

```kotlin
// Old binding
private var _binding: FragmentChatBinding? = null

// New binding
private var _binding: FragmentChatTelegramBinding? = null

// Old adapter
private lateinit var chatAdapter: ChatAdapter

// New adapter
private lateinit var chatAdapter: TelegramChatAdapter
```

### 4. Testing

After integration, test these scenarios:
1. Send a message
2. Receive a message
3. Switch to dark mode
4. Scroll through history
5. Rotate device

### 5. Customization (Optional)

#### Change bubble colors:
```xml
<!-- res/values/colors_chat.xml -->
<color name="chat_bubble_out_color">#YOUR_COLOR</color>
```

#### Adjust animation speed:
```kotlin
// TelegramChatAdapter.kt
private const val ANIMATION_DURATION = 200L // Faster
```

#### Modify bubble shape:
```xml
<!-- bg_chat_bubble_out.xml -->
<corners 
    android:topLeftRadius="24dp"  <!-- More rounded -->
    android:topRightRadius="8dp"
    android:bottomLeftRadius="24dp"
    android:bottomRightRadius="24dp"/>
```

## Troubleshooting

### Messages not appearing
- Check socket connection
- Verify deviceId is set correctly
- Check database insertion

### Animations stuttering
- Reduce ANIMATION_DURATION
- Check device performance
- Reduce animation complexity

### Layout issues
- Verify all drawable resources exist
- Check dimension values
- Test on different screen sizes

### Dark mode not working
- Verify values-night folder exists
- Check color references
- Test theme switching

## Support

For issues or questions, refer to `TELEGRAM_CHAT_IMPLEMENTATION.md` for detailed documentation.

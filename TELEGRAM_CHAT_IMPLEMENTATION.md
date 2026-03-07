# Telegram-Inspired Chat Implementation

## Overview

This implementation provides a complete Telegram-inspired chat interface for the QuakeAlert app, featuring modern message bubbles, smooth animations, and an enhanced user experience.

## Key Features

### 1. **Chat Bubbles (Telegram-inspired)**
- Rounded corner bubbles with distinctive tails
- Different styles for incoming and outgoing messages
- Material Design color scheme with dark mode support
- Proper elevation and shadows

### 2. **Message Layout**
- **Outgoing messages**: Green bubbles aligned to the right
- **Incoming messages**: White/dark bubbles aligned to the left with avatar
- Sender name support (for group chats)
- Time stamps in a subtle format
- Message status indicators (checkmarks)

### 3. **Animations**
- Slide-in animations for new messages (from left/right based on message type)
- Fade-in effects for smooth appearance
- Staggered animation delays for multiple messages
- Smooth scroll-to-bottom behavior

### 4. **Input View**
- Material Design card-based input field
- Rounded corners for modern look
- Send button with scale animation based on input state
- Support for multi-line messages
- Optional attachment button (hidden by default)

### 5. **ItemDecoration**
- Proper spacing between messages
- Group spacing for better visual separation
- Follows Telegram's spacing guidelines

### 6. **Dark Mode Support**
- Full dark mode implementation
- Automatic color switching based on system theme
- Proper contrast ratios for readability

## File Structure

```
app/src/main/
├── java/id/my/bananapixel/quakealert/ui/
│   ├── TelegramChatAdapter.kt          # Enhanced adapter with animations
│   ├── TelegramChatFragment.kt         # Main chat fragment
│   └── chat/
│       └── ChatMessageItemDecoration.kt # Spacing decorator
├── res/
│   ├── layout/
│   │   ├── fragment_chat_telegram.xml  # Main chat layout
│   │   ├── item_chat_incoming.xml      # Incoming message item
│   │   └── item_chat_outgoing.xml      # Outgoing message item
│   ├── drawable/
│   │   ├── bg_chat_bubble_in.xml       # Incoming bubble background
│   │   ├── bg_chat_bubble_out.xml      # Outgoing bubble background
│   │   ├── ic_check.xml                # Checkmark icon
│   │   └── ic_person.xml               # Avatar placeholder
│   ├── values/
│   │   ├── colors_chat.xml             # Chat color definitions
│   │   ├── dimens_chat.xml             # Chat dimensions
│   │   └── strings_chat.xml            # Chat strings
│   └── values-night/
│       └── colors_chat.xml             # Dark mode colors
```

## Implementation Details

### Chat Bubble Design

#### Outgoing Messages
```xml
- Background: #8BC34A (green)
- Corners: Top-left (18dp), Top-right (4dp), Bottom-left (18dp), Bottom-right (18dp)
- Tail: Small rotated square at top-right corner
- Text color: White
- Alignment: Right
```

#### Incoming Messages
```xml
- Background: #FFFFFF (light) / #2C2C2E (dark)
- Corners: Top-left (4dp), Top-right (18dp), Bottom-left (18dp), Bottom-right (18dp)
- Tail: Small rotated square at top-left corner
- Text color: Black (light) / White (dark)
- Alignment: Left
- Avatar: 36dp circle
```

### Animation System

The adapter implements three types of animations:

1. **Alpha Animation**: Fades in from 0 to 1
2. **Translation X Animation**: Slides in from ±100dp to 0
3. **Translation Y Animation**: Slides up from 30dp to 0

All animations:
- Duration: 300ms
- Interpolator: DecelerateInterpolator
- Stagger delay: 50ms per message (up to 3 messages)

### Time Formatting

Messages display time in three formats:
- **Today**: HH:mm (e.g., "14:30")
- **This year**: MMM d, HH:mm (e.g., "Jan 15, 14:30")
- **Other years**: MMM d yyyy, HH:mm (e.g., "Jan 15 2023, 14:30")

## Integration Guide

### Option 1: Replace Existing Chat Fragment

1. **Update navigation graph or fragment container** to use `TelegramChatFragment`:

```kotlin
// In your activity or parent fragment
supportFragmentManager.beginTransaction()
    .replace(R.id.fragment_container, TelegramChatFragment())
    .commit()
```

2. **Update existing references** from `ChatFragment` to `TelegramChatFragment`

### Option 2: Use as Alternative Layout

Keep both implementations and let users choose:

```kotlin
class ChatFragment : Fragment() {
    private var useTelegramStyle = true // Add preference
    
    override fun onCreateView(...): View {
        return if (useTelegramStyle) {
            FragmentChatTelegramBinding.inflate(inflater, container, false).root
        } else {
            FragmentChatBinding.inflate(inflater, container, false).root
        }
    }
}
```

### Option 3: Gradual Migration

1. **Step 1**: Add new layouts alongside existing ones
2. **Step 2**: Test with beta users
3. **Step 3**: Migrate existing `ChatFragment` to use new adapter
4. **Step 4**: Remove old layout files

## Usage Example

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Add Telegram-style chat fragment
        supportFragmentManager.beginTransaction()
            .add(R.id.container, TelegramChatFragment())
            .commit()
    }
}
```

## Customization

### Colors

Modify `res/values/colors_chat.xml`:

```xml
<color name="chat_bubble_out_color">#YOUR_COLOR</color>
<color name="chat_bubble_in_color">#YOUR_COLOR</color>
```

### Bubble Shape

Edit `bg_chat_bubble_out.xml` and `bg_chat_bubble_in.xml`:

```xml
<corners 
    android:topLeftRadius="18dp"
    android:topRightRadius="4dp"
    android:bottomLeftRadius="18dp"
    android:bottomRightRadius="18dp"/>
```

### Animation Duration

Modify constants in `TelegramChatAdapter.kt`:

```kotlin
private const val ANIMATION_DURATION = 300L  // Change this
private const val ANIMATION_DELAY = 50L      // Change this
```

### Spacing

Edit `res/values/dimens_chat.xml`:

```xml
<dimen name="chat_message_spacing">4dp</dimen>
<dimen name="chat_group_spacing">12dp</dimen>
```

## Key Differences from Telegram

While heavily inspired by Telegram, this implementation adapts to QuakeAlert's needs:

1. **Simplified**: No stickers, voice messages, or media gallery
2. **Material Design**: Uses Material components for consistency
3. **Socket.IO**: Uses Socket.IO instead of Telegram's MTProto protocol
4. **Single Purpose**: Optimized for emergency communication

## Performance Considerations

- **ViewHolder pattern**: Efficient view recycling
- **DiffUtil**: Only animates changed items
- **Animation tracking**: Prevents re-animating recycled views
- **Lazy loading**: Messages load as needed

## Accessibility

- Content descriptions for icons
- Proper label associations
- Screen reader support
- High contrast mode support

## Testing Checklist

- [ ] Messages sent successfully
- [ ] Messages received and displayed
- [ ] Animations play smoothly
- [ ] Dark mode works correctly
- [ ] Scroll behavior is correct
- [ ] Keyboard doesn't overlap input
- [ ] Long messages wrap properly
- [ ] Time stamps format correctly
- [ ] Avatar displays properly

## Known Limitations

1. No file attachment support (yet - can be added)
2. No message editing or deletion
3. No read receipts
4. No typing indicators
5. No message forwarding

## Future Enhancements

- [ ] Add voice message support
- [ ] Implement message reactions
- [ ] Add reply/quote functionality
- [ ] Implement message search
- [ ] Add message selection/copy
- [ ] Support for rich text formatting
- [ ] Image/file attachments
- [ ] Typing indicators
- [ ] Read receipts
- [ ] Message editing/deletion

## Credits

- Design inspiration: Telegram Messenger
- Implementation: QuakeAlert Team
- Based on Telegram's open-source Android client

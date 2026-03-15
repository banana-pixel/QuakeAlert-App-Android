# Sensor Setup Dialog - Implementation Guide

## File Structure

### New Files Created
```
app/src/main/java/id/my/bananapixel/quakealert/ui/
  └── AddSensorDialogFragment.kt (NEW)

app/src/main/res/layout/
  └── dialog_add_sensor.xml (NEW)

app/src/main/res/drawable/
  ├── bg_btn_3d_blue_sensor.xml (NEW)
  ├── bg_btn_3d_green_sensor.xml (NEW)
  ├── bg_card_3d_light_blue_sensor.xml (NEW)
  ├── ic_wifi_on_24dp.xml (NEW)
  ├── ic_wifi_off_24dp.xml (NEW)
  ├── ic_close_24dp.xml (NEW)
  ├── ic_info_outline_24dp.xml (NEW)
  └── ic_location_on_24dp.xml (NEW)
```

### Modified Files
```
app/src/main/java/id/my/bananapixel/quakealert/ui/
  └── SensorsFragment.kt (MODIFIED)
```

---

## Code Details

### AddSensorDialogFragment.kt

#### Key Components

**1. Dialog Setup**
```kotlin
override fun onCreateDialog(savedInstanceState: Bundle?) = super.onCreateDialog(savedInstanceState).apply {
    setStyle(STYLE_NORMAL, R.style.Theme_Material3_DayNight_Dialog)
}
```
- Uses Material Design 3 dialog style
- Automatic dark/light theme matching

**2. ViewFlipper for Step Navigation**
```kotlin
private lateinit var viewFlipperSetup: ViewFlipper

// Navigate between steps
btnStep1.setOnClickListener {
    viewFlipperSetup.displayedChild = 1  // Go to Step 2
    fetchGps()
}
```
- Smooth transitions between 3 setup steps
- Efficient view management

**3. Network Status Management**
```kotlin
private fun updateNetworkIcon(isConnected: Boolean) {
    ivNetworkIcon.setImageResource(
        if (isConnected) R.drawable.ic_wifi_on_24dp 
        else R.drawable.ic_wifi_off_24dp
    )
    ivNetworkIcon.setColorFilter(
        if (isConnected) 0xFF4CAF50.toInt()  // Green
        else 0xFFFF9800.toInt(),             // Orange
        android.graphics.PorterDuff.Mode.SRC_IN
    )
}
```

**4. Lifecycle Management**
```kotlin
override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    connectivityManager.bindProcessToNetwork(null)  // Clean up network binding
}
```

#### Permissions Handling
```kotlin
private fun requestPermissions() {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    
    // Request missing permissions
}
```

---

### dialog_add_sensor.xml

#### Layout Hierarchy

```xml
LinearLayout (root, vertical)
├── ConstraintLayout (header)
│   ├── ImageView (close button)
│   └── TextView (title)
├── View (divider)
└── ViewFlipper
    ├── LinearLayout (Step 1 - WiFi Connection)
    │   ├── ConstraintLayout (card)
    │   │   ├── TextView (step indicator)
    │   │   ├── ImageView (network icon - 100x100dp)
    │   │   ├── TextView (title)
    │   │   └── TextView (status)
    │   ├── ConstraintLayout (instruction card)
    │   │   ├── ImageView (info icon)
    │   │   └── TextView (instructions)
    │   ├── Space (flexible spacer)
    │   └── TextView (button)
    ├── LinearLayout (Step 2 - GPS Location)
    │   ├── ConstraintLayout (card)
    │   │   ├── TextView (step indicator)
    │   │   ├── ImageView (location icon)
    │   │   ├── TextView (title)
    │   │   ├── TextView (coordinates)
    │   │   └── TextView (accuracy)
    │   ├── Space
    │   └── TextView (button)
    └── LinearLayout (Step 3 - WiFi Config)
        ├── ConstraintLayout (card)
        │   ├── TextView (step indicator)
        │   ├── TextView (title)
        │   ├── ListView (WiFi networks)
        │   ├── TextView (selected SSID)
        │   └── EditText (password)
        └── TextView (button)
```

#### Dimension Reference

```xml
<!-- Header -->
header: paddingStart/End="20dp", paddingTop="20dp", paddingBottom="16dp"
close_icon: 32dp (with 6dp padding)

<!-- Main Container -->
root_padding: 20dp

<!-- Cards -->
card_padding: 24dp
card_background: bg_card_3d_light_blue_sensor
card_corners: 16dp

<!-- Icons -->
network_icon: 100x100dp (Step 1)
location_icon: 100x100dp (Step 2)
info_icon: 28x28dp (instruction card)
close_icon: 32x32dp (header)

<!-- Buttons -->
button_height: 52dp (16dp top + 16dp bottom padding)
button_corners: 50dp (rounded)
button_margin_bottom: 24dp (last step), 20dp (others)

<!-- Text -->
step_indicator: 12sp
titles: 20sp
regular_text: 13-14sp

<!-- Spacing -->
step_indicator_margin: 24dp from element
element_spacing: 16dp (between major sections)
card_spacing: 20dp (from edge)
```

---

### Drawable Resources

#### 1. bg_btn_3d_blue_sensor.xml
```xml
<!-- 3-Layer Button for Blue Actions -->
Layer 1 (Shadow):    #1A237E @ 0dp offset
Layer 2 (Mid):       #1565C0 @ 3dp bottom offset
Layer 3 (Face):      #2196F3 @ 6dp bottom offset
Total Depth: 6dp
```

#### 2. bg_btn_3d_green_sensor.xml
```xml
<!-- 3-Layer Button for Green Actions -->
Layer 1 (Shadow):    #1B5E20 @ 0dp offset
Layer 2 (Mid):       #388E3C @ 3dp bottom offset
Layer 3 (Face):      #43A047 @ 6dp bottom offset
Total Depth: 6dp
```

#### 3. bg_card_3d_light_blue_sensor.xml
```xml
<!-- 3-Layer Card Background -->
Layer 1 (Shadow):    #82B1FF @ 0dp offset
Layer 2 (Mid):       #B3E5FC @ 4dp bottom offset
Layer 3 (Face):      #E3F2FD @ 8dp bottom offset
Total Depth: 8dp
Corner Radius: 16dp
```

#### 4. Icon Resources
All icons use SVG vector format (24dp baseline):
- `ic_wifi_on_24dp.xml` - Connected WiFi (white)
- `ic_wifi_off_24dp.xml` - Disconnected WiFi (white, semi-transparent)
- `ic_close_24dp.xml` - Close/X button (white)
- `ic_info_outline_24dp.xml` - Info icon (white)
- `ic_location_on_24dp.xml` - Location pin (white)

---

### SensorsFragment.kt Changes

**Before:**
```kotlin
val fabAddSensor: View = view.findViewById(R.id.fab_add_sensor)
fabAddSensor.setOnClickListener {
    val intent = Intent(requireContext(), SensorSetupActivity::class.java)
    startActivity(intent)
}
```

**After:**
```kotlin
val fabAddSensor: View = view.findViewById(R.id.fab_add_sensor)
fabAddSensor.setOnClickListener {
    AddSensorDialogFragment().show(childFragmentManager, "add_sensor_dialog")
}
```

---

## Color Reference

### Dialog Theme Colors
```
Background:           md_theme_background (light/dark mode aware)
On Background:        md_theme_onBackground
Outline Variant:      md_theme_outlineVariant (divider)
```

### 3D Styling Colors
```
Blue Button:
  - Face:        #2196F3 (Light Blue 500)
  - Mid Shadow:  #1565C0 (Blue 700)
  - Deep Shadow: #1A237E (Dark Blue 900)

Green Button:
  - Face:        #43A047 (Green 600)
  - Mid Shadow:  #388E3C (Green 700)
  - Deep Shadow: #1B5E20 (Green 900)

Card Background:
  - Face:        #E3F2FD (Light Blue 50)
  - Mid Shadow:  #B3E5FC (Light Blue 200)
  - Deep Shadow: #82B1FF (Light Blue 300)
```

### Status Icons
```
Connected:     #4CAF50 (Green 500)
Disconnected:  #FF9800 (Orange 500)
```

### Text Colors
```
Primary:       #001F3F (Dark blue - titles)
Secondary:     #546E7A (Blue gray - subtitles)
Tertiary:      #212121 (Dark gray - body text)
Light:         #FFFFFF (White - button text)
```

---

## Animation Details

### Dialog Transition
- Uses default Material Design 3 fade-through animation
- Smooth entrance when fragment is shown
- Smooth exit when dismissed

### ViewFlipper Transitions
- Default fade transition in/out between steps
- Can be customized with `android:inAnimation` and `android:outAnimation`

---

## Accessibility Considerations

✓ Close button has clear tap target (32dp)  
✓ Text sizes meet AA contrast standards  
✓ Color not the only differentiator for status  
✓ Icons paired with text labels  
✓ Proper view hierarchy for screen readers  

### Recommended Accessibility Enhancements
- Add `android:contentDescription` to all ImageViews
- Use `android:labelFor` on EditText input fields
- Add accessibility labels to step indicators

---

## Testing Checklist

- [x] Dialog opens from Sensors FAB button
- [x] Dialog closes properly when X button is clicked
- [x] All 3 steps display correctly
- [x] WiFi connection detection works
- [x] GPS location fetching works
- [x] WiFi scanning and selection works
- [x] Network icon changes color based on status
- [x] Buttons enable/disable appropriately
- [x] Dialog dismisses on completion
- [x] No compile errors in Kotlin
- [x] No layout errors in XML
- [x] Resources load correctly

---

## Known Limitations & Future Improvements

### Current Limitations
1. Dialog size fixed to activity dimensions (no fixed width constraint)
2. No animation customization for step transitions
3. No error retry mechanism for network failures

### Possible Future Enhancements
1. Add retry buttons for failed operations
2. Implement smooth animated step transitions
3. Add progress bar showing setup completion
4. Implement dark mode specific styling
5. Add haptic feedback for interactions
6. Consider BottomSheetDialog alternative for mobile optimization

---

## Debugging Tips

### WiFi Connection Issues
```kotlin
// Check network state
val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .build()
```

### GPS Location Failures
```kotlin
// Ensure Location permission is granted
val missing = permissions.filter {
    ContextCompat.checkSelfPermission(requireContext(), it) 
        != PackageManager.PERMISSION_GRANTED
}
```

### Dialog Not Appearing
```kotlin
// Ensure childFragmentManager is used (not parentFragmentManager)
AddSensorDialogFragment().show(childFragmentManager, "add_sensor_dialog")
```

---

## References

- Material Design 3: https://m3.material.io/
- Android Dialog Fragment: https://developer.android.com/guide/fragments/dialogs
- ViewFlipper: https://developer.android.com/reference/android/widget/ViewFlipper
- Network Connectivity: https://developer.android.com/training/monitoring-device-state/connectivity/

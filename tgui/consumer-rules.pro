# ThemeDescription looks up cell fields by name via reflection (e.g. "textView", "checkBox"),
# and property animators use @Keep setters. Keep UI members readable for R8 in host apps.
-keepclassmembers class org.telegram.ui.Cells.** { <fields>; }
-keepclassmembers class org.telegram.ui.Components.** { <fields>; }
-keepclassmembers class org.telegram.ui.ActionBar.** { <fields>; }
-keepclassmembers class ** {
    @androidx.annotation.Keep *;
}

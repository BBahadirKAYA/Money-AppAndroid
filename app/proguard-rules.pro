# Keep WebView-related classes
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
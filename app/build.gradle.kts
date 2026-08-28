plugins { id("com.android.application") }

android { namespace = "com.guanlixitong.app"; compileSdk = 35
 defaultConfig { applicationId = "com.guanlixitong.app"; minSdk = 24; targetSdk = 35; versionCode = 2; versionName = "2.0" }
}

dependencies {
 implementation("androidx.core:core-splashscreen:1.0.1")
 implementation("androidx.core:core:1.13.1")
 implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}

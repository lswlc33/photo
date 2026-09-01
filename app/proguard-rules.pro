# Release builds run R8 with shrinking and resource shrinking enabled.
#
# No keep rules are needed today: the app has no reflection, no JNI, no
# Gson/Moshi models and no Service/Provider entry points beyond MainActivity,
# which the manifest already keeps. Media3, MIUIX and Backdrop ship their own
# consumer rules.
#
# Add rules here - not to the default file - if a release build ever starts
# behaving differently from debug.

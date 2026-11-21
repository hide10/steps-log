# 歩数ログ アプリ固有の ProGuard/R8 ルール
#
# Room / WorkManager / Health Connect / Glance / Compose / Billing は
# AAR メタデータで自前のルールを同梱済み。ここにはアプリ固有の分だけ書く。

# --- kotlinx-serialization（JSON バックアップの読み書き） ---
# domain/Backup.kt の @Serializable クラス群。R8 で serializer が
# 落ちると、バックアップの読み込みが実行時に静かに壊れるため保持する。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep @kotlinx.serialization.Serializable class com.kbcode.steps.** { *; }
-keepclassmembers class com.kbcode.steps.**$$serializer { *; }
-keepclassmembers class com.kbcode.steps.**$Companion { *; }

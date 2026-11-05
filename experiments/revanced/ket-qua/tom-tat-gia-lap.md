# Kết quả chạy thử trên giả lập

- Android: 15 (API 35)
- Kiến trúc CPU giả lập: x86_64,arm64-v8a
- Google Play Services: versionName=24.23.35 (190800-646585959)
- microG/GmsCore có trên máy không: 
- YouTube cài sẵn: package:com.google.android.apps.youtube.music package:com.google.android.youtube
- Video thử: https://www.youtube.com/watch?v=dQw4w9WgXcQ

```
Bộ vá: v6.2.1 ngày 2026-06-02T18:30:00
SHA-256 patches.rvp: 50f41e4656b7374a1c8fe5579fd107f697f86d7f13fc6cd194cf3821761dc4b4
khoá ReVanced:       A783 5DFC ACA1 4BDA 3CD6  EDD3 633C 6920 FBE6 A2FF
khoá ReVanced: uid                      ReVanced <contact@revanced.app>
[GNUPG:] VALIDSIG 61C079D8CD06B2B14B4A462F1EDEE52215B296C4 2026-06-02 1780411520 0 4 0 1 10 00 A7835DFCACA14BDA3CD6EDD3633C6920FBE6A2FF
Chữ ký PGP của bộ vá: HỢP LỆ
CLI: revanced-cli-6.0.0-all.jar
YouTube: 20.40.45
Chỉ số bản vá GmsCore support cho YouTube: 284
youtube-revanced-khong-microg: 0 chỗ trong mã (dex) gọi app.revanced.android.gms, 391 chỗ gọi com.google.android.gms
youtube-revanced-chuan: 16 chỗ trong mã (dex) gọi app.revanced.android.gms, 387 chỗ gọi com.google.android.gms
```

## 1-youtube-goc

- Gói: `com.google.android.youtube`
- Cài đặt: Performing Streamed Install Success 
- Sau 60 giây app còn chạy: có (pid 3946)
- Màn hình đang ở: `topResumedActivity=ActivityRecord{e3b6025 u0 com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.InternalMainActivity t8}`
- Trạng thái phát (MediaSession): state=PlaybackState {state=PLAYING(3), position=1069
- Số luồng âm thanh đang phát trên máy: 1
- Văng app: không
- Chữ trên màn hình lúc 25 giây: Video player|Expand Mini Player|Enter fullscreen|Skip|Skip ad|More options|TikTok: Videos, Music &amp; LIVE|Install|0 minutes 11 seconds of 0 minutes 11 seconds|LIKE AD|SHARE AD|Close ad panel|Learn more|
- Chữ trên màn hình lúc 60 giây: Video player|Expand Mini Player|0 minutes 13 seconds of 3 minutes 33 seconds|Rick Astley, Official Artist Channel 4.56M subscribers|Subscribe to Rick Astley.|like this video along with 19,491,840 other people|Dislike this video|Share|Download|Sponsored - See MYQORZO™ info &amp; resources&#10;Still experiencing symptoms? Ask your doctor about MYQORZO™ (aficamten).&#10;myqorzo.com - Learn more - Link opens in new window|

<details><summary>Log quan trọng (đầu)</summary>

```
11-05 05:01:18.568  1586  2164 I Backup  : [GmsBackupAccountManager] Backup account not found in gmscore.
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: Usage reporting check failed [CONTEXT service_id=299 ]
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: java.util.concurrent.ExecutionException: java.util.concurrent.TimeoutException
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at etvr.s(:com.google.android.gms@242335041@24.23.35 (190800-646585959):21)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at etvr.get(:com.google.android.gms@242335041@24.23.35 (190800-646585959):3)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at etzy.a(:com.google.android.gms@242335041@24.23.35 (190800-646585959):2)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at etyp.r(:com.google.android.gms@242335041@24.23.35 (190800-646585959):10)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at bjvu.h(:com.google.android.gms@242335041@24.23.35 (190800-646585959):1)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at bjvr.run(:com.google.android.gms@242335041@24.23.35 (190800-646585959):7)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at etyf.call(:com.google.android.gms@242335041@24.23.35 (190800-646585959):3)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at etxh.a(:com.google.android.gms@242335041@24.23.35 (190800-646585959):3)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at etyv.run(:com.google.android.gms@242335041@24.23.35 (190800-646585959):21)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at angs.c(:com.google.android.gms@242335041@24.23.35 (190800-646585959):50)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at angs.run(:com.google.android.gms@242335041@24.23.35 (190800-646585959):76)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:644)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at anmg.run(:com.google.android.gms@242335041@24.23.35 (190800-646585959):8)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at java.lang.Thread.run(Thread.java:1012)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: Caused by: java.util.concurrent.TimeoutException
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at dkjz.run(:com.google.android.gms@242335041@24.23.35 (190800-646585959):3)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at android.os.Handler.handleCallback(Handler.java:959)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at android.os.Handler.dispatchMessage(Handler.java:100)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at bphk.nR(:com.google.android.gms@242335041@24.23.35 (190800-646585959):1)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at bphk.dispatchMessage(:com.google.android.gms@242335041@24.23.35 (190800-646585959):138)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at android.os.Looper.loopOnce(Looper.java:232)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at android.os.Looper.loop(Looper.java:317)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at android.app.ActivityThread.main(ActivityThread.java:8705)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at java.lang.reflect.Method.invoke(Native Method)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:580)
11-05 05:01:18.646  1224  2656 E GmsCoreLogger: 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:886)
11-05 05:01:20.138  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__enable_dark_launch_gmscore_server_policy
11-05 05:01:20.139  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__enable_gmscore_server_policy
11-05 05:01:20.140  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__gmscore_canary_charging_relax_threshold_ms
11-05 05:01:20.140  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__gmscore_canary_idle_relax_threshold_ms
11-05 05:01:20.140  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__gmscore_canary_wifi_relax_threshold_ms
11-05 05:01:20.140  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__gmscore_charging_relax_threshold_ms
11-05 05:01:20.140  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__gmscore_idle_relax_threshold_ms
11-05 05:01:20.140  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , AutoUpdateCodegen__gmscore_wifi_relax_threshold_ms
11-05 05:01:20.200  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , DroidguardAcquire__enable_droidguard_token_generation_for_all_device_classes
11-05 05:01:20.200  1860  2379 D HeterodyneSyncer: updateFlag: com.google.android.finsky.regular, 84132530, 0, , DroidguardAcquire__enable_invalid_droidguard_handle_retry
```
</details>

## 2-revanced-khong-microg

- Gói: `app.revanced.android.youtube`
- Cài đặt: Performing Streamed Install Success 
- Sau 60 giây app còn chạy: có (pid 6094)
- Màn hình đang ở: `topResumedActivity=ActivityRecord{31e33d4 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t7}`
- Trạng thái phát (MediaSession): không có
- Số luồng âm thanh đang phát trên máy: 0
- Văng app: 11-05 05:04:25.549  5141  5413 E AndroidRuntime: Process: app.revanced.android.youtube, PID: 5141 11-05 05:04:25.549  5141  5413 E AndroidRuntime: java.lang.SecurityException: PackageVerificationRslt: not allowed: pkg=app.revanced.android.youtube, sha256=[1b1183e21d12e2b7e42b5be4b5cdb14f86c40c90d2b4729074387b871fed5799], atk=false, ver=242335041.true 11-05 05:04:25.549  5141  5413 E AndroidRuntime: 	at android.os.Parcel.createExceptionOrNull(Parcel.java:3242) 
- Chữ trên màn hình lúc 25 giây: At a glance|Thu, Nov 5|Home|Phone|Messages|Play Store|Chrome|Camera|Google search|Google app|Voice search|Google Lens|
- Chữ trên màn hình lúc 60 giây: At a glance|Thu, Nov 5|Home|Phone|Messages|Play Store|Chrome|Camera|Google search|Google app|Voice search|Google Lens|
- **Mở lại lần 2** (sau 40 giây): còn chạy: KHÔNG; màn hình: `topResumedActivity=ActivityRecord{31e33d4 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t7}`; phát: không có; văng: 11-05 05:05:36.514  6094  6401 E AndroidRuntime: Process: app.revanced.android.youtube, PID: 6094 11-05 05:05:36.514  6094  6401 E AndroidRuntime: java.lang.SecurityException: PackageVerificationRslt: not allowed: pkg=app.revanced.android.youtube, sha256=[1b1183e21d12e2b7e42b5be4b5cdb14f86c40c90d2b4729074387b871fed5799], atk=false, ver=242335041.true 
- Chữ trên màn hình khi mở lại: At a glance|Thu, Nov 5|Home|Phone|Messages|Play Store|Chrome|Camera|Google search|Google app|Voice search|Google Lens|

```
11-05 05:05:24.702  6094  6217 D nativeloader: Configuring clns-8 for other apk /system/framework/com.android.media.remotedisplay.jar. target_sdk_version=34, uses_libraries=ALL, library_path=/product/priv-app/PrebuiltGms
11-05 05:05:24.703  6094  6217 D nativeloader: Configuring clns-9 for other apk /system/framework/com.android.location.provider.jar. target_sdk_version=34, uses_libraries=ALL, library_path=/product/priv-app/PrebuiltGmsCo
11-05 05:05:24.742  6094  6217 D nativeloader: Configuring product-clns-10 for unbundled product apk /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk. target_sdk_version=34, uses_libraries=, library_path=/product/pr
11-05 05:05:24.802  6094  6217 D nativeloader: Load /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk!/lib/x86_64/libconscrypt_gmscore_jni.so using ns product-clns-10 from class loader (caller=/product/priv-app/Prebu
11-05 05:05:24.856  6094  6217 I ProviderInstaller: Installed default security provider GmsCore_OpenSSL
11-05 05:05:24.972  6094  6130 E GoogleApiManager: java.lang.SecurityException: PackageVerificationRslt: not allowed: pkg=app.revanced.android.youtube, sha256=[1b1183e21d12e2b7e42b5be4b5cdb14f86c40c90d2b4729074387b871fed
11-05 05:05:27.450  6094  6094 W YouTube : Invalid playback type; streaming data is not playable
11-05 05:05:27.830  1224  2395 E DatabaseUtils: java.lang.SecurityException: GoogleCertificatesRslt: not allowed: pkg=app.revanced.android.youtube, sha256=[1b1183e21d12e2b7e42b5be4b5cdb14f86c40c90d2b4729074387b871fed5799
11-05 05:05:27.842  6094  6094 E Auth    : java.lang.SecurityException: GoogleCertificatesRslt: not allowed: pkg=app.revanced.android.youtube, sha256=[1b1183e21d12e2b7e42b5be4b5cdb14f86c40c90d2b4729074387b871fed5799], at
11-05 05:05:28.840  6094  6094 E YouTube : Failed to fetch kids onboarding status, finishing the App.
11-05 05:05:36.514  6094  6401 E AndroidRuntime: java.lang.SecurityException: PackageVerificationRslt: not allowed: pkg=app.revanced.android.youtube, sha256=[1b1183e21d12e2b7e42b5be4b5cdb14f86c40c90d2b4729074387b871fed57
11-05 05:05:38.832   549   609 I PackageManager: Update system package com.google.android.gms code path from /product/priv-app/PrebuiltGmsCore to /data/app/~~sIxWFeGKAiDHIb5HkH7BHg==/com.google.android.gms-JLKz-1XGIWIHEI
```

<details><summary>Log quan trọng (đầu)</summary>

```
11-05 05:03:29.045   549   565 W system_server: ApkAssets: Deleting an ApkAssets object '<empty> and /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk' with 2 weak references
11-05 05:03:33.331  3338  3345 W gs.intelligence: ApkAssets: Deleting an ApkAssets object '<empty> and /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk' with 1 weak references
11-05 05:03:34.300  1067  1073 W ndroid.settings: ApkAssets: Deleting an ApkAssets object '<empty> and /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk' with 1 weak references
11-05 05:03:35.960  1067  1073 W ndroid.settings: ApkAssets: Deleting an ApkAssets object '<empty> and /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk' with 1 weak references
11-05 05:03:36.027   549   587 I ActivityManager: Start proc 4907:com.google.android.settings.intelligence/u0a142 for content provider {com.google.android.settings.intelligence/com.google.android.settings.intelligence.modules.gmscoreproxy.impl.PhenotypeProxyContentProvider}
11-05 05:03:37.719  1067  1073 W ndroid.settings: ApkAssets: Deleting an ApkAssets object '<empty> and /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk' with 1 weak references
11-05 05:03:38.001  1224  4570 W NetworkScheduler: Immediate task was not started com.google.android.gms/.droidguard.DroidGuardGcmTaskService{u=0 tag="dg_task" trigger=window{start=0s,end=1s,earliest=0s,latest=0s} requirements=[NET_CONNECTED,PREFERRED_NET_CONNECTED,PREFERRED_CHARGING,REACHABILITY_URIS:tcp://www.googleapis.com:443] attributes=[] scheduled=0s last_run=N/A exec_window_multiplier=1.0000 jid=N/A status=PENDING retries=0 client_lib=GMS_TASK_SCHEDULER-242335000}. Rescheduling immediate tasks can cause excessive battery drain.
11-05 05:03:43.036   549   565 W system_server: ApkAssets: Deleting an ApkAssets object '<empty> and /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk' with 1 weak references
11-05 05:03:50.799  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:03:50.803  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:03:50.806  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:03:50.810  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:03:50.815  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:03:50.817  1433  1675 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:03:50.940   549   609 I ArtService: Adjusting the compiler filter for 'app.revanced.android.youtube' from 'speed-profile' to 'verify' because there is no valid profile
11-05 05:03:50.979  4984  4988 I artd    : Dex parent of /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.apk is not writable: Permission denied
11-05 05:03:50.980  4984  4988 I artd    : GetBestInfo checking odex in dalvik-cache (/data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.dex)
11-05 05:03:50.981  4984  4988 I artd    : Loading /data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.dex with executable: 0
11-05 05:03:50.981  4984  4988 I artd    : OatFileAssistant test for existing oat file /data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.dex: File /data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.vdex does not exist.
11-05 05:03:50.981  4984  4988 I artd    : GetBestInfo checking odex next to the dex file (/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.odex)
11-05 05:03:50.981  4984  4988 I artd    : Loading /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.odex with executable: 0
11-05 05:03:50.981  4984  4988 I artd    : OatFileAssistant test for existing oat file /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.odex: File /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.vdex does not exist.
11-05 05:03:50.981  4984  4988 I artd    : GetBestInfo checking vdex in dalvik-cache (/data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.vdex)
11-05 05:03:50.981  4984  4988 I artd    : unable to open vdex file /data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.vdex: File /data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.vdex does not exist.
11-05 05:03:50.981  4984  4988 I artd    : OatFileAssistant test for existing oat file /data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.vdex: File /data/dalvik-cache/x86_64/data@app@~~4gBTtErAU672SgCaVg6p7A==@app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==@base.apk@classes.vdex does not exist.
11-05 05:03:50.981  4984  4988 I artd    : GetBestInfo checking vdex next to the dex file (/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.vdex)
11-05 05:03:50.981  4984  4988 I artd    : unable to open vdex file /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.vdex: File /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.vdex does not exist.
11-05 05:03:50.981  4984  4988 I artd    : OatFileAssistant test for existing oat file /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.vdex: File /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.vdex does not exist.
11-05 05:03:50.981  4984  4988 I artd    : GetBestInfo checking dm (/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.dm)
11-05 05:03:50.981  4984  4988 W ziparchive: Unable to open '/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.dm': No such file or directory
11-05 05:03:50.981  4984  4988 I artd    : OatFileAssistant test for existing oat file /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.dm: I/O error
11-05 05:03:50.981  4984  4988 I artd    : GetBestInfo checking dm (/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.dm)
11-05 05:03:50.981  4984  4988 W ziparchive: Unable to open '/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.dm': No such file or directory
11-05 05:03:50.981  4984  4988 I artd    : OatFileAssistant test for existing oat file /data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.dm: I/O error
11-05 05:03:51.091  4984  4988 I artd    : Running dex2oat: /apex/com.android.art/bin/art_exec --drop-capabilities --set-task-profile=Dex2OatBootComplete --set-priority=background --keep-fds=6:7:8:9:10:11:12 -- /apex/com.android.art/bin/dex2oat64 --zip-fd=6 --zip-location=/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.apk --oat-fd=7 --oat-location=/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.odex --output-vdex-fd=8 --swap-fd=9 --class-loader-context-fds=10:11:12 --class-loader-context=PCL[]{PCL[/system/framework/org.apache.http.legacy.jar]#PCL[/system_ext/framework/androidx.window.extensions.jar]#PCL[/system_ext/framework/androidx.window.sidecar.jar]} --classpath-dir=/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ== --instruction-set=x86_64 --instruction-set-features=default --instruction-set-variant=x86_64 --compiler-filter=verify --compilation-reason=install --compact-dex-level=none --max-image-block-size=524288 --resolve-startup-const-strings=true --generate-mini-debug-info --runtime-arg -Xtarget-sdk-version:35 --runtime-arg -Xhidden-api-policy:enabled --runtime-arg -Xms64m --runtime-arg -Xmx512m --comments=app-name:app.revanced.android.youtube,app-version-name:20.40.45,app-version-code:1557278144,art-version:350820300
11-05 05:03:51.091  4984  4988 I artd    : Opened FDs: 6:/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.apk 7:/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.odex.juKQKL.tmp 8:/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.vdex.CKdLcc.tmp 9:/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.odex.swap.J0g9Za.tmp 10:/system_ext/framework/androidx.window.sidecar.jar 11:/system_ext/framework/androidx.window.extensions.jar 12:/system/framework/org.apache.http.legacy.jar 
11-05 05:03:51.280  4990  4990 W dex2oat64: /apex/com.android.art/bin/dex2oat64 --zip-fd=6 --zip-location=/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/base.apk --oat-fd=7 --oat-location=/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ==/oat/x86_64/base.odex --output-vdex-fd=8 --swap-fd=9 --class-loader-context-fds=10:11:12 --class-loader-context=PCL[]{PCL[/system/framework/org.apache.http.legacy.jar]#PCL[/system_ext/framework/androidx.window.extensions.jar]#PCL[/system_ext/framework/androidx.window.sidecar.jar]} --classpath-dir=/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ== --instruction-set=x86_64 --instruction-set-features=default --instruction-set-variant=x86_64 --compiler-filter=verify --compilation-reason=install --compact-dex-level=none --max-image-block-size=524288 --resolve-startup-const-strings=true --generate-mini-debug-info --runtime-arg -Xtarget-sdk-version:35 --runtime-arg -Xhidden-api-policy:enabled --runtime-arg -Xms64m --runtime-arg -Xmx512m --comments=app-name:app.revanced.android.youtube,app-version-name:20.40.45,app-version-code:1557278144,art-version:350820300
11-05 05:03:51.280  4990  4990 I dex2oat64: /apex/com.android.art/bin/dex2oat64 --output-vdex-fd=8 --class-loader-context-fds=10:11:12 --class-loader-context=PCL[]{PCL[/system/framework/org.apache.http.legacy.jar]#PCL[/system_ext/framework/androidx.window.extensions.jar]#PCL[/system_ext/framework/androidx.window.sidecar.jar]} --classpath-dir=/data/app/~~4gBTtErAU672SgCaVg6p7A==/app.revanced.android.youtube-LDfIGjb8FZKXGthPu7FDiQ== --compiler-filter=verify --compilation-reason=install --compact-dex-level=none --max-image-block-size=524288 --resolve-startup-const-strings=true --generate-mini-debug-info --comments=app-name:app.revanced.android.youtube,app-version-name:20.40.45,app-version-code:1557278144,art-version:350820300
11-05 05:03:59.996  2711  2711 W binder:2711_4: type=1400 audit(0.0:77): avc:  denied  { read } for  name="u:object_r:system_adbd_prop:s0" dev="tmpfs" ino=314 scontext=u:r:gmscore_app:s0:c512,c768 tcontext=u:object_r:system_adbd_prop:s0 tclass=file permissive=0 app=com.google.android.gms
11-05 05:03:59.996  2711  2711 W binder:2711_4: type=1400 audit(0.0:78): avc:  denied  { read } for  name="u:object_r:adbd_prop:s0" dev="tmpfs" ino=59 scontext=u:r:gmscore_app:s0:c512,c768 tcontext=u:object_r:adbd_prop:s0 tclass=file permissive=0 app=com.google.android.gms
```
</details>

## 3-revanced-chuan-can-microg

- Gói: `app.revanced.android.youtube`
- Cài đặt: Performing Streamed Install Success 
- Sau 60 giây app còn chạy: có (pid 7514)
- Màn hình đang ở: `topResumedActivity=ActivityRecord{23f8d87 u0 com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity t12}`
- Trạng thái phát (MediaSession): không có
- Số luồng âm thanh đang phát trên máy: 0
- Văng app: không
- Chữ trên màn hình lúc 25 giây: Welcome to Chrome|Sign in to browse easier across devices|Add account to device|Use without an account|By continuing, you agree to the Terms of Service. To help improve the app, Chrome sends usage and crash data to Google. Manage|
- Chữ trên màn hình lúc 60 giây: Welcome to Chrome|Sign in to browse easier across devices|Add account to device|Use without an account|By continuing, you agree to the Terms of Service. To help improve the app, Chrome sends usage and crash data to Google. Manage|
- **Mở lại lần 2** (sau 40 giây): còn chạy: có; màn hình: `topResumedActivity=ActivityRecord{d609f52 u0 com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity t13}`; phát: không có; văng: không
- Chữ trên màn hình khi mở lại: Welcome to Chrome|Sign in to browse easier across devices|Add account to device|Use without an account|By continuing, you agree to the Terms of Service. To help improve the app, Chrome sends usage and crash data to Google. Manage|

```
11-05 05:07:36.551  7514  7514 I revanced: GmsCoreSupport: GmsCore was not found
11-05 05:07:36.557  7514  7514 I revanced: GmsCoreSupport: Opening link: https://github.com/revanced/gmscore/releases/latest
11-05 05:07:36.589  7514  7514 E YouTube : 	at app.revanced.extension.shared.GmsCoreSupport$GmsCore.open(GmsCoreSupport.java:275)
11-05 05:07:36.589  7514  7514 E YouTube : 	at app.revanced.extension.shared.GmsCoreSupport$GmsCore.checkInstallation(GmsCoreSupport.java:176)
11-05 05:07:36.589  7514  7514 E YouTube : 	at app.revanced.extension.shared.GmsCoreSupport$GmsCore.check(GmsCoreSupport.java:145)
11-05 05:07:36.589  7514  7514 E YouTube : 	at app.revanced.extension.shared.GmsCoreSupport.checkGmsCore(GmsCoreSupport.java:52)
11-05 05:07:39.193  8081  8197 E DynamiteModule: Invalid GmsCore APK, remote loading disabled.
11-05 05:07:59.259  6469  6476 W gle.android.gms: ApkAssets: Deleting an ApkAssets object '<empty> and /product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk' with 1 weak references
11-05 05:08:02.257   549  1007 W ActivityManager: Scheduling restart of crashed service com.google.android.gms/.common.stats.GmsCoreStatsService in 140999ms for start-requested
11-05 05:08:02.766  8664  8664 I GmsCoreXrpcWrapper: (REDACTED) Returning a channel provider with trafficStatsTag=%d trafficStatsUid=%d
11-05 05:08:04.242  8664  8712 I GmsCoreVoilaTileGrpc: (REDACTED) xrpc compression enabled: %s
```

<details><summary>Log quan trọng (đầu)</summary>

```
11-05 05:06:14.631  6469  7007 W Ads     : isZeroOutAdid: Name not found for package: app.revanced.android.youtube
11-05 05:06:14.643  6469  6520 W Ads     : isZeroOutAdid: Name not found for package: app.revanced.android.youtube
11-05 05:06:15.347  6469  7007 W FA-SVC  : Failed to get uid, app: app.revanced.android.youtube
11-05 05:06:16.676   549  1488 D ShortcutService: Getting shortcuts for launcher= com.google.android.asuser=0 pkg=app.revanced.android.youtube
11-05 05:06:16.676   549  1488 D ShortcutService: getShortcutsInnerLocked() returned empty results because app.revanced.android.youtube isn't loaded
11-05 05:06:16.944  6676  6676 W binder:6676_4: type=1400 audit(0.0:98): avc:  denied  { read } for  name="u:object_r:system_adbd_prop:s0" dev="tmpfs" ino=314 scontext=u:r:gmscore_app:s0:c512,c768 tcontext=u:object_r:system_adbd_prop:s0 tclass=file permissive=0 app=com.google.android.gms
11-05 05:06:16.952  6676  6676 W binder:6676_4: type=1400 audit(0.0:99): avc:  denied  { read } for  name="u:object_r:adbd_prop:s0" dev="tmpfs" ino=59 scontext=u:r:gmscore_app:s0:c512,c768 tcontext=u:object_r:adbd_prop:s0 tclass=file permissive=0 app=com.google.android.gms
11-05 05:06:17.465  6469  7083 I GmsCoreXrpcWrapper: (REDACTED) Returning a channel provider with trafficStatsTag=%d trafficStatsUid=%d
11-05 05:06:17.487  6469  7083 I Backup  : [GmsBackupAccountManager] Backup account not found in gmscore.
11-05 05:06:19.020  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:06:19.027   549   609 I ArtService: Adjusting the compiler filter for 'app.revanced.android.youtube' from 'speed-profile' to 'verify' because there is no valid profile
11-05 05:06:19.028  6388  6388 I artd    : Dex parent of /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.apk is not writable: Permission denied
11-05 05:06:19.028  6388  6388 I artd    : GetBestInfo checking odex in dalvik-cache (/data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.dex)
11-05 05:06:19.028  6388  6388 I artd    : Loading /data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.dex with executable: 0
11-05 05:06:19.028  6388  6388 I artd    : OatFileAssistant test for existing oat file /data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.dex: File /data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.vdex does not exist.
11-05 05:06:19.028  6388  6388 I artd    : GetBestInfo checking odex next to the dex file (/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.odex)
11-05 05:06:19.028  6388  6388 I artd    : Loading /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.odex with executable: 0
11-05 05:06:19.028  6388  6388 I artd    : OatFileAssistant test for existing oat file /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.odex: File /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.vdex does not exist.
11-05 05:06:19.028  6388  6388 I artd    : GetBestInfo checking vdex in dalvik-cache (/data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.vdex)
11-05 05:06:19.028  6388  6388 I artd    : unable to open vdex file /data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.vdex: File /data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.vdex does not exist.
11-05 05:06:19.028  6388  6388 I artd    : OatFileAssistant test for existing oat file /data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.vdex: File /data/dalvik-cache/x86_64/data@app@~~QpG3nhwoGqWdMGrpmFz7Cg==@app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==@base.apk@classes.vdex does not exist.
11-05 05:06:19.028  6388  6388 I artd    : GetBestInfo checking vdex next to the dex file (/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.vdex)
11-05 05:06:19.028  6388  6388 I artd    : unable to open vdex file /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.vdex: File /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.vdex does not exist.
11-05 05:06:19.028  6388  6388 I artd    : OatFileAssistant test for existing oat file /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.vdex: File /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.vdex does not exist.
11-05 05:06:19.028  6388  6388 I artd    : GetBestInfo checking dm (/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.dm)
11-05 05:06:19.028  6388  6388 W ziparchive: Unable to open '/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.dm': No such file or directory
11-05 05:06:19.028  6388  6388 I artd    : OatFileAssistant test for existing oat file /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.dm: I/O error
11-05 05:06:19.028  6388  6388 I artd    : GetBestInfo checking dm (/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.dm)
11-05 05:06:19.028  6388  6388 W ziparchive: Unable to open '/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.dm': No such file or directory
11-05 05:06:19.028  6388  6388 I artd    : OatFileAssistant test for existing oat file /data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.dm: I/O error
11-05 05:06:19.029  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:06:19.034  6388  6388 I artd    : Running dex2oat: /apex/com.android.art/bin/art_exec --drop-capabilities --set-task-profile=Dex2OatBootComplete --set-priority=background --keep-fds=6:7:8:9:10:11:12 -- /apex/com.android.art/bin/dex2oat64 --zip-fd=6 --zip-location=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.apk --oat-fd=7 --oat-location=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.odex --output-vdex-fd=8 --swap-fd=9 --class-loader-context-fds=10:11:12 --class-loader-context=PCL[]{PCL[/system/framework/org.apache.http.legacy.jar]#PCL[/system_ext/framework/androidx.window.extensions.jar]#PCL[/system_ext/framework/androidx.window.sidecar.jar]} --classpath-dir=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg== --instruction-set=x86_64 --instruction-set-features=default --instruction-set-variant=x86_64 --compiler-filter=verify --compilation-reason=install --compact-dex-level=none --max-image-block-size=524288 --resolve-startup-const-strings=true --generate-mini-debug-info --runtime-arg -Xtarget-sdk-version:35 --runtime-arg -Xhidden-api-policy:enabled --runtime-arg -Xms64m --runtime-arg -Xmx512m --comments=app-name:app.revanced.android.youtube,app-version-name:20.40.45,app-version-code:1557278144,art-version:350820300
11-05 05:06:19.034  6388  6388 I artd    : Opened FDs: 6:/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.apk 7:/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.odex.L1brnI.tmp 8:/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.vdex.xQHTv0.tmp 9:/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.odex.swap.YJJwOO.tmp 10:/system_ext/framework/androidx.window.sidecar.jar 11:/system_ext/framework/androidx.window.extensions.jar 12:/system/framework/org.apache.http.legacy.jar 
11-05 05:06:19.037  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:06:19.046  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:06:19.047  1433  1675 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:06:19.050  1433  1442 D MediaGrants: Removed 0 media_grants for 0 user for [app.revanced.android.youtube]. Reason: Mode changed: android:read_external_storage
11-05 05:06:19.091  7335  7335 W dex2oat64: /apex/com.android.art/bin/dex2oat64 --zip-fd=6 --zip-location=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.apk --oat-fd=7 --oat-location=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/oat/x86_64/base.odex --output-vdex-fd=8 --swap-fd=9 --class-loader-context-fds=10:11:12 --class-loader-context=PCL[]{PCL[/system/framework/org.apache.http.legacy.jar]#PCL[/system_ext/framework/androidx.window.extensions.jar]#PCL[/system_ext/framework/androidx.window.sidecar.jar]} --classpath-dir=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg== --instruction-set=x86_64 --instruction-set-features=default --instruction-set-variant=x86_64 --compiler-filter=verify --compilation-reason=install --compact-dex-level=none --max-image-block-size=524288 --resolve-startup-const-strings=true --generate-mini-debug-info --runtime-arg -Xtarget-sdk-version:35 --runtime-arg -Xhidden-api-policy:enabled --runtime-arg -Xms64m --runtime-arg -Xmx512m --comments=app-name:app.revanced.android.youtube,app-version-name:20.40.45,app-version-code:1557278144,art-version:350820300
11-05 05:06:19.092  7335  7335 I dex2oat64: /apex/com.android.art/bin/dex2oat64 --output-vdex-fd=8 --class-loader-context-fds=10:11:12 --class-loader-context=PCL[]{PCL[/system/framework/org.apache.http.legacy.jar]#PCL[/system_ext/framework/androidx.window.extensions.jar]#PCL[/system_ext/framework/androidx.window.sidecar.jar]} --classpath-dir=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg== --compiler-filter=verify --compilation-reason=install --compact-dex-level=none --max-image-block-size=524288 --resolve-startup-const-strings=true --generate-mini-debug-info --comments=app-name:app.revanced.android.youtube,app-version-name:20.40.45,app-version-code:1557278144,art-version:350820300
11-05 05:06:27.262   549   609 I ArtService: Dexopt result: [packageName = app.revanced.android.youtube] DexContainerFileDexoptResult{dexContainerFile=/data/app/~~QpG3nhwoGqWdMGrpmFz7Cg==/app.revanced.android.youtube-vmKUbtstDAGTqa0Qmus6Rg==/base.apk, primaryAbi=true, abi=x86_64, actualCompilerFilter=verify, status=PERFORMED, dex2oatWallTimeMillis=8227, dex2oatCpuTimeMillis=6640, sizeBytes=56634248, sizeBeforeBytes=0, extendedStatusFlags=[]}
```
</details>


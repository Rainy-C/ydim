# YDim 1.1

直切后端自动顺序：ADB 特殊授权 → Shizuku / Sui → LSPosed → Root。

LSPosed：在模块列表启用 YDim，作用域只选 `com.android.systemui`（System UI），然后重启 SystemUI 或重启手机。

ADB：

```sh
adb shell pm grant com.yucve.ydim android.permission.WRITE_SECURE_SETTINGS
```

普通模式：没有直切权限时，磁贴会打开系统极暗设置页。

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const YDimApp());
}

class QuickSettingsBridge {
  static const MethodChannel _channel =
      MethodChannel('com.yucve.ydim/quick_settings');

  static Future<String> requestAddTile() async {
    return await _channel.invokeMethod<String>('requestAddTile') ?? 'unknown';
  }

  static Future<String> openExtremeDimSettings() async {
    return await _channel.invokeMethod<String>('openExtremeDimSettings') ??
        'unknown';
  }

  static Future<Map<String, dynamic>> getStatus() async {
    final value = await _channel.invokeMapMethod<String, dynamic>('getStatus');
    return Map<String, dynamic>.from(value ?? const <String, dynamic>{});
  }

  static Future<Map<String, dynamic>> toggleExtremeDim() async {
    final value =
        await _channel.invokeMapMethod<String, dynamic>('toggleExtremeDim');
    return Map<String, dynamic>.from(value ?? const <String, dynamic>{});
  }

  static Future<String> requestShizukuPermission() async {
    return await _channel.invokeMethod<String>('requestShizukuPermission') ??
        'unknown';
  }
}

class YDimApp extends StatelessWidget {
  const YDimApp({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFF5056A8),
      brightness: Brightness.light,
    );

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'YDim',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: colorScheme,
        scaffoldBackgroundColor: colorScheme.surface,
        appBarTheme: AppBarTheme(
          backgroundColor: colorScheme.surface,
          foregroundColor: colorScheme.onSurface,
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
        cardTheme: CardThemeData(
          elevation: 0,
          margin: EdgeInsets.zero,
          color: colorScheme.surfaceContainerLow,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
          ),
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(52),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(18),
            ),
            textStyle: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _adding = false;
  bool _opening = false;
  bool _toggling = false;
  bool _loading = true;
  Map<String, dynamic> _status = const <String, dynamic>{};

  bool get _enabled => _status['enabled'] == true;

  @override
  void initState() {
    super.initState();
    _refreshStatus();
  }

  Future<void> _refreshStatus() async {
    if (!Platform.isAndroid) return;
    if (mounted) setState(() => _loading = true);
    try {
      final status = await QuickSettingsBridge.getStatus();
      if (mounted) setState(() => _status = status);
    } on PlatformException {
      if (mounted) _showMessage('无法读取授权状态');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _addTile() async {
    if (!Platform.isAndroid || _adding) return;
    setState(() => _adding = true);
    try {
      final result = await QuickSettingsBridge.requestAddTile();
      if (!mounted) return;
      switch (result) {
        case 'added':
          _showMessage('已添加到控制中心');
          break;
        case 'already_added':
          _showMessage('磁贴已经在控制中心中');
          break;
        case 'manual':
          _showManualAddDialog();
          break;
        case 'not_added':
          _showMessage('已取消添加');
          break;
        default:
          _showMessage('添加失败，请在控制中心编辑页手动添加');
      }
    } on PlatformException {
      if (mounted) _showMessage('添加失败，请在控制中心编辑页手动添加');
    } finally {
      if (mounted) setState(() => _adding = false);
    }
  }

  Future<void> _toggle() async {
    if (!Platform.isAndroid || _toggling) return;
    setState(() => _toggling = true);
    try {
      final result = await QuickSettingsBridge.toggleExtremeDim();
      if (!mounted) return;
      if (result['success'] == true) {
        _showMessage('${result['message']} · ${result['backend']}');
        await _refreshStatus();
      } else {
        _showMessage('没有直切权限，已打开系统极暗页面');
        await _openExtremeDimSettings(showFallbackMessage: false);
      }
    } on PlatformException {
      if (mounted) _showMessage('切换失败');
    } finally {
      if (mounted) setState(() => _toggling = false);
    }
  }

  Future<void> _requestShizuku() async {
    final result = await QuickSettingsBridge.requestShizukuPermission();
    if (!mounted) return;
    switch (result) {
      case 'granted':
        _showMessage('Shizuku 已授权');
        break;
      case 'denied':
        _showMessage('Shizuku 授权被拒绝，请到 Shizuku 中重新允许');
        break;
      case 'not_running':
        _showMessage('请先启动 Shizuku');
        break;
      case 'unsupported':
        _showMessage('Shizuku 版本过旧');
        break;
      default:
        _showMessage('无法请求 Shizuku 授权');
    }
    await _refreshStatus();
  }

  Future<void> _openExtremeDimSettings({bool showFallbackMessage = true}) async {
    if (!Platform.isAndroid || _opening) return;
    setState(() => _opening = true);
    try {
      final result = await QuickSettingsBridge.openExtremeDimSettings();
      if (mounted && showFallbackMessage && result == 'fallback') {
        _showMessage('当前系统未提供极暗页面，已打开显示设置');
      }
    } on PlatformException {
      if (mounted) _showMessage('无法打开系统设置');
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  void _showManualAddDialog() {
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.dashboard_customize_outlined),
        title: const Text('手动添加磁贴'),
        content: const Text(
          '下拉控制中心，进入编辑页面，找到“极暗模式”，再拖入常用快捷开关区域。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('知道了'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('YDim', style: TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(
            tooltip: '刷新状态',
            onPressed: _loading ? null : _refreshStatus,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _refreshStatus,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
            children: [
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: _enabled
                      ? colors.primaryContainer
                      : colors.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(32),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        color: _enabled ? colors.primary : colors.secondary,
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: Icon(
                        _enabled ? Icons.dark_mode_rounded : Icons.light_mode_rounded,
                        color: _enabled ? colors.onPrimary : colors.onSecondary,
                        size: 30,
                      ),
                    ),
                    const SizedBox(height: 22),
                    Text(
                      _enabled ? '极暗模式已开启' : '极暗模式已关闭',
                      style: theme.textTheme.headlineSmall?.copyWith(
                        color: _enabled
                            ? colors.onPrimaryContainer
                            : colors.onSurface,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '可通过 ADB 授权、Shizuku、LSPosed 或 Root 直接切换。',
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: _enabled
                            ? colors.onPrimaryContainer
                            : colors.onSurfaceVariant,
                        height: 1.5,
                      ),
                    ),
                    const SizedBox(height: 20),
                    FilledButton.icon(
                      onPressed: _toggling ? null : _toggle,
                      icon: _toggling
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Icon(_enabled ? Icons.power_settings_new : Icons.dark_mode),
                      label: Text(_toggling
                          ? '正在切换'
                          : (_enabled ? '关闭极暗模式' : '开启极暗模式')),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.add_to_photos_outlined, color: colors.primary),
                          const SizedBox(width: 12),
                          Text(
                            '控制中心磁贴',
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        '点一下直接切换。没有直切权限时，会自动打开系统极暗设置。',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: colors.onSurfaceVariant,
                          height: 1.45,
                        ),
                      ),
                      const SizedBox(height: 18),
                      FilledButton.icon(
                        onPressed: _adding ? null : _addTile,
                        icon: _adding
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.add_circle_outline),
                        label: Text(_adding ? '正在请求添加' : '添加到控制中心'),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Card(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.admin_panel_settings_outlined,
                              color: colors.primary),
                          const SizedBox(width: 12),
                          Text(
                            '授权通道',
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      _StatusRow(
                        icon: Icons.terminal_rounded,
                        title: 'ADB 特殊授权',
                        value: _status['adb']?.toString() ?? '检查中',
                      ),
                      _StatusRow(
                        icon: Icons.account_tree_outlined,
                        title: 'Shizuku / Sui',
                        value: _status['shizuku']?.toString() ?? '检查中',
                        action: (_status['shizuku']?.toString() == '等待授权' ||
                                _status['shizuku']?.toString() == '未运行')
                            ? TextButton(
                                onPressed: _requestShizuku,
                                child: const Text('授权'),
                              )
                            : null,
                      ),
                      _StatusRow(
                        icon: Icons.extension_outlined,
                        title: 'LSPosed',
                        value: _status['lsposed']?.toString() ?? '检查中',
                      ),
                      _StatusRow(
                        icon: Icons.security_rounded,
                        title: 'Root',
                        value: _status['root']?.toString() ?? '检查中',
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '自动优先级：ADB → Shizuku / Sui → LSPosed → Root。',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: colors.onSurfaceVariant,
                          height: 1.45,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Card(
                child: ListTile(
                  contentPadding: const EdgeInsets.fromLTRB(20, 14, 12, 14),
                  leading: Container(
                    width: 46,
                    height: 46,
                    decoration: BoxDecoration(
                      color: colors.secondaryContainer,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Icon(
                      Icons.settings_brightness_outlined,
                      color: colors.onSecondaryContainer,
                    ),
                  ),
                  title: const Text(
                    '打开系统极暗设置',
                    style: TextStyle(fontWeight: FontWeight.w800),
                  ),
                  subtitle: const Padding(
                    padding: EdgeInsets.only(top: 4),
                    child: Text('普通模式下仍可作为系统设置入口使用'),
                  ),
                  trailing: IconButton.filledTonal(
                    tooltip: '打开',
                    onPressed: _opening ? null : _openExtremeDimSettings,
                    icon: _opening
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.open_in_new_rounded),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Text(
                    'LSPosed 使用：在模块列表启用 YDim，作用域只选择“系统界面 / System UI”，随后重启系统界面或重启手机。',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: colors.onSurfaceVariant,
                      height: 1.5,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatusRow extends StatelessWidget {
  const _StatusRow({
    required this.icon,
    required this.title,
    required this.value,
    this.action,
  });

  final IconData icon;
  final String title;
  final String value;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Icon(icon, size: 20, color: colors.onSurfaceVariant),
          const SizedBox(width: 12),
          Expanded(
            child: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
          ),
          Text(value, style: TextStyle(color: colors.onSurfaceVariant)),
          if (action != null) ...[
            const SizedBox(width: 4),
            action!,
          ],
        ],
      ),
    );
  }
}

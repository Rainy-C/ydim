from pathlib import Path

path = Path('android/app/build.gradle.kts')
if not path.exists():
    raise SystemExit('[-] 未找到 android/app/build.gradle.kts')

text = path.read_text(encoding='utf-8')

def block_end(source: str, start: int) -> int:
    level = 0
    for index in range(start, len(source)):
        char = source[index]
        if char == '{':
            level += 1
        elif char == '}':
            level -= 1
            if level == 0:
                return index
    raise ValueError('Gradle 块不完整')

if 'isCoreLibraryDesugaringEnabled = true' not in text:
    marker = 'compileOptions {'
    start = text.find(marker)
    if start < 0:
        raise SystemExit('[-] 未找到 compileOptions，无法自动写入 desugaring 配置')
    end = block_end(text, start)
    text = text[:end] + '    isCoreLibraryDesugaringEnabled = true\n' + text[end:]

if 'dev.rikka.shizuku:api:13.1.5' not in text:
    marker = 'dependencies {'
    start = text.find(marker)
    if start < 0:
        raise SystemExit('[-] 未找到 dependencies，无法自动写入依赖')
    end = block_end(text, start)
    deps = '''    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    compileOnly(files("libs/xposed-api-82.jar"))
'''
    text = text[:end] + deps + text[end:]

path.write_text(text, encoding='utf-8')
print('[+] Gradle 依赖已写入')

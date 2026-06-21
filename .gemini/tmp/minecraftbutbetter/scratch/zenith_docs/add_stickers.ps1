$mapping = @{
    "D:\Me\Zenith игра\Zenith обучалка\0. Алгоритмы для начинающих.md" = "1f9e0"
    "D:\Me\Zenith игра\Zenith обучалка\1. JVM и Память.md" = "2615-fe0f"
    "D:\Me\Zenith игра\Zenith обучалка\2. Конвейер OpenGL.md" = "1f5bc-fe0f"
    "D:\Me\Zenith игра\Zenith обучалка\3. GPU-Driven Rendering.md" = "1f680"
    "D:\Me\Zenith игра\Zenith обучалка\4. Процедурная физика и Математика.md" = "1f4d0"
    "D:\Me\Zenith игра\Zenith обучалка\5. Event-Driven архитектура.md" = "1f504"
    "D:\Me\Zenith игра\Zenith обучалка\6. UI и Рендеринг шрифтов.md" = "1f5b1-fe0f"
    "D:\Me\Zenith игра\Zenith обучалка\7. Многопоточность.md" = "1f9f5"
    "D:\Me\Zenith игра\Zenith обучалка\8. Шпаргалка для собеседований.md" = "1f4dd"
    "D:\Me\Zenith игра\Zenith обучалка\9. RPG и Генерация лута.md" = "1f48e"
    "D:\Me\Zenith игра\Zenith обучалка\10. Физика ViewModel и Магнетизм.md" = "1f9f2"
    "D:\Me\Zenith игра\Zenith обучалка\Железо и Архитектура ПК\0. Введение.md" = "1f331"
    "D:\Me\Zenith игра\Zenith обучалка\Железо и Архитектура ПК\1. CPU.md" = "1f5a5-fe0f"
    "D:\Me\Zenith игра\Zenith обучалка\Железо и Архитектура ПК\2. Память.md" = "1f4be"
    "D:\Me\Zenith игра\Zenith обучалка\Железо и Архитектура ПК\3. PCIe.md" = "1f6e3-fe0f"
    "D:\Me\Zenith игра\Zenith обучалка\Железо и Архитектура ПК\4. Операционная система.md" = "1f4bb"
}

foreach ($path in $mapping.Keys) {
    if (Test-Path $path) {
        $code = $mapping[$path]
        $header = "---`nsticker: emoji//$code`ncolor: var(--mk-color-base-0)`n---`n`n"
        $content = Get-Content $path -Raw -Encoding UTF8
        # Prevent double adding if already exists (basic check)
        if (-not $content.StartsWith("---`nsticker:")) {
            $newContent = $header + $content
            Set-Content $path -Value $newContent -Encoding UTF8
            Write-Host "Added sticker to $path"
        } else {
            Write-Host "Sticker already exists in $path"
        }
    } else {
        Write-Warning "File not found: $path"
    }
}

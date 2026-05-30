$f = "d:\VS Code projects\MCDG\src\main\java\com\mcdg\ui\HudStateFormatter.java"
$lines = Get-Content $f
$before = $lines[0..99]
$after = $lines[100..($lines.Count-1)]
$result = $before + '        return text;' + $after
Set-Content $f $result
"Done. Lines: $($result.Count)"
(Get-Content $f)[97..104]
